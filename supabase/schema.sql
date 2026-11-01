-- ============================================================================
-- SUNSHINE HOSPITALS - SISTEMA DE GESTÃO DE FILAS & RECEPÇÃO
-- Schema PostgreSQL para Supabase (com Realtime + Histórico Perpetuado)
-- ============================================================================

create extension if not exists "pgcrypto";

-- 1. Unidades de Serviço (Preparado para futura expansão: Radiologia, Consultórios, etc.)
create table if not exists public.service_units (
  id text primary key,
  name text not null,
  description text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.service_units (id, name, description, is_active)
values
  ('recepcao', 'Recepção Central', 'Atendimento, Faturação de Seguros, Particulares e Agendamentos', true),
  ('radiologia', 'Radiologia & Imagiologia', 'Unidade reservada para expansão futura', false),
  ('consultorios', 'Gabinetes Médicos', 'Unidade reservada para expansão futura', false)
on conflict (id) do nothing;

-- 2. Ciclos de Contagem (Permite Zerar Contadores sem apagar o Histórico para BI/Faturação)
create table if not exists public.queue_cycles (
  id uuid primary key default gen_random_uuid(),
  service_unit_id text not null references public.service_units(id) default 'recepcao',
  cycle_label text not null,
  is_current boolean not null default true,
  started_at timestamptz not null default now(),
  closed_at timestamptz,
  reset_by text default 'Administrador'
);

insert into public.queue_cycles (service_unit_id, cycle_label, is_current)
select 'recepcao', 'Turno Atual - ' || to_char(now(), 'DD/MM/YYYY'), true
where not exists (select 1 from public.queue_cycles where is_current = true);

-- 3. Balcões Dinâmicos (A, B, C, D ou mais, com nomes editáveis e filas de transbordo)
create table if not exists public.counters (
  id text primary key,
  code text not null,
  name text not null,
  subtitle text not null,
  service_unit_id text not null references public.service_units(id) default 'recepcao',
  primary_queues text[] not null default '{}',
  secondary_queues text[] not null default '{}',
  support_mode_active boolean not null default true,
  is_active boolean not null default true,
  display_order integer not null default 1,
  updated_at timestamptz not null default now()
);

insert into public.counters (id, code, name, subtitle, primary_queues, secondary_queues, support_mode_active, is_active, display_order)
values
  (
    'counter-a',
    'A',
    'Balcão A',
    'Atendimento Particular & Apoio Seguros',
    array['PARTICULAR'],
    array['SEGURO'],
    true,
    true,
    1
  ),
  (
    'counter-b',
    'B',
    'Balcão B',
    'Faturação de Seguros & Apoio Particular',
    array['SEGURO'],
    array['PARTICULAR'],
    true,
    true,
    2
  ),
  (
    'counter-c',
    'C',
    'Balcão C',
    'Faturação de Seguros & Apoio Particular',
    array['SEGURO'],
    array['PARTICULAR'],
    true,
    true,
    3
  ),
  (
    'counter-d',
    'D',
    'Balcão D',
    'Agendamentos, Cotações & Outros Serviços',
    array['OUTROS'],
    array['PARTICULAR', 'SEGURO'],
    false,
    true,
    4
  )
on conflict (id) do nothing;

-- 4. Senhas (Tickets) - Nunca são apagadas no Reset Diário para preservar auditoria e SLA
create table if not exists public.tickets (
  id uuid primary key default gen_random_uuid(),
  cycle_id uuid not null references public.queue_cycles(id),
  service_unit_id text not null references public.service_units(id) default 'recepcao',
  category text not null check (category in ('PARTICULAR', 'SEGURO', 'OUTROS')),
  sub_service text not null,
  sub_service_label text not null,
  sequence_number integer not null,
  display_code text not null,
  status text not null check (status in ('WAITING', 'CALLED', 'IN_SERVICE', 'AWAITING_EXAM', 'COMPLETED', 'NO_SHOW')) default 'WAITING',
  is_return_from_exam boolean not null default false,
  reuse_count integer not null default 0,
  assigned_counter_id text references public.counters(id),
  assigned_counter_name text,
  call_count integer not null default 0,
  created_at timestamptz not null default now(),
  queued_at timestamptz not null default now(),
  called_at timestamptz,
  service_started_at timestamptz,
  exam_started_at timestamptz,
  returned_from_exam_at timestamptz,
  completed_at timestamptz
);

create index if not exists idx_tickets_cycle_status on public.tickets(cycle_id, status);
create index if not exists idx_tickets_display_code on public.tickets(display_code, cycle_id);

-- 5. Habilitar Row Level Security (Acesso público para demo / rede interna do hospital)
alter table public.service_units enable row level security;
alter table public.queue_cycles enable row level security;
alter table public.counters enable row level security;
alter table public.tickets enable row level security;

drop policy if exists "Allow public access to service_units" on public.service_units;
create policy "Allow public access to service_units" on public.service_units for all using (true) with check (true);

drop policy if exists "Allow public access to queue_cycles" on public.queue_cycles;
create policy "Allow public access to queue_cycles" on public.queue_cycles for all using (true) with check (true);

drop policy if exists "Allow public access to counters" on public.counters;
create policy "Allow public access to counters" on public.counters for all using (true) with check (true);

drop policy if exists "Allow public access to tickets" on public.tickets;
create policy "Allow public access to tickets" on public.tickets for all using (true) with check (true);

-- 6. Ativar Realtime no Supabase para atualização imediata (TV, Recepção e Totem)
alter publication supabase_realtime add table public.counters;
alter publication supabase_realtime add table public.tickets;
alter publication supabase_realtime add table public.queue_cycles;

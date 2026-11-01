import { defineConfig, Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import fs from 'node:fs';
import path from 'node:path';

const STATE_FILE = path.resolve(process.cwd(), 'server-queue-state.json');

interface CounterConfig {
  id: string;
  code: string;
  name: string;
  subtitle: string;
  serviceUnitId: string;
  primaryQueues: string[];
  secondaryQueues: string[];
  supportModeActive: boolean;
  isActive: boolean;
  displayOrder: number;
}

interface QueueCycle {
  id: string;
  serviceUnitId: string;
  cycleLabel: string;
  isCurrent: boolean;
  startedAt: string;
  closedAt?: string | null;
  resetBy: string;
}

interface Ticket {
  id: string;
  cycleId: string;
  serviceUnitId: string;
  category: 'PARTICULAR' | 'SEGURO' | 'OUTROS';
  subService: string;
  subServiceLabel: string;
  sequenceNumber: number;
  displayCode: string;
  status: 'WAITING' | 'CALLED' | 'IN_SERVICE' | 'AWAITING_EXAM' | 'COMPLETED' | 'NO_SHOW';
  isReturnFromExam: boolean;
  reuseCount: number;
  assignedCounterId?: string | null;
  assignedCounterName?: string | null;
  callCount: number;
  createdAt: string;
  queuedAt: string;
  calledAt?: string | null;
  serviceStartedAt?: string | null;
  examStartedAt?: string | null;
  returnedFromExamAt?: string | null;
  completedAt?: string | null;
}

interface ServerState {
  counters: CounterConfig[];
  cycles: QueueCycle[];
  tickets: Ticket[];
  updatedAt: number;
}

function getInitialState(): ServerState {
  const now = Date.now();
  const min = (m: number) => new Date(now - m * 60 * 1000).toISOString();

  const yesterdayCycle: QueueCycle = {
    id: 'cycle-hist-yesterday',
    serviceUnitId: 'recepcao',
    cycleLabel: 'Turno Anterior (Ontem)',
    isCurrent: false,
    startedAt: min(1440),
    closedAt: min(600),
    resetBy: 'Admin Recepção',
  };

  const currentCycle: QueueCycle = {
    id: 'cycle-active-today',
    serviceUnitId: 'recepcao',
    cycleLabel: `Turno Atual — ${new Date().toLocaleDateString('pt-PT')}`,
    isCurrent: true,
    startedAt: min(240),
    closedAt: null,
    resetBy: 'Sistema Automático',
  };

  const counters: CounterConfig[] = [
    {
      id: 'counter-a',
      code: 'A',
      name: 'Balcão A',
      subtitle: 'Particular & Apoio Seguros',
      serviceUnitId: 'recepcao',
      primaryQueues: ['PARTICULAR'],
      secondaryQueues: ['SEGURO'],
      supportModeActive: true,
      isActive: true,
      displayOrder: 1,
    },
    {
      id: 'counter-b',
      code: 'B',
      name: 'Balcão B',
      subtitle: 'Faturação de Seguros & Apoio Particular',
      serviceUnitId: 'recepcao',
      primaryQueues: ['SEGURO'],
      secondaryQueues: ['PARTICULAR'],
      supportModeActive: true,
      isActive: true,
      displayOrder: 2,
    },
    {
      id: 'counter-c',
      code: 'C',
      name: 'Balcão C',
      subtitle: 'Faturação de Seguros & Apoio Particular',
      serviceUnitId: 'recepcao',
      primaryQueues: ['SEGURO'],
      secondaryQueues: ['PARTICULAR'],
      supportModeActive: true,
      isActive: true,
      displayOrder: 3,
    },
    {
      id: 'counter-d',
      code: 'D',
      name: 'Balcão D',
      subtitle: 'Agendamento, Cotação Simples & Outros',
      serviceUnitId: 'recepcao',
      primaryQueues: ['OUTROS'],
      secondaryQueues: ['PARTICULAR', 'SEGURO'],
      supportModeActive: false,
      isActive: true,
      displayOrder: 4,
    },
  ];

  const tickets: Ticket[] = [
    {
      id: 'hist-1',
      cycleId: yesterdayCycle.id,
      serviceUnitId: 'recepcao',
      category: 'PARTICULAR',
      subService: 'CONSULTA_PARTICULAR',
      subServiceLabel: 'Marcação de Consulta (Particular)',
      sequenceNumber: 1,
      displayCode: 'P-001',
      status: 'COMPLETED',
      isReturnFromExam: false,
      reuseCount: 0,
      assignedCounterId: 'counter-a',
      assignedCounterName: 'Balcão A',
      callCount: 1,
      createdAt: min(1300),
      queuedAt: min(1300),
      calledAt: min(1294),
      serviceStartedAt: min(1293),
      completedAt: min(1285),
    },
    {
      id: 'curr-p-1',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'PARTICULAR',
      subService: 'CONSULTA_PARTICULAR',
      subServiceLabel: 'Marcação de Consulta (Particular)',
      sequenceNumber: 1,
      displayCode: 'P-001',
      status: 'IN_SERVICE',
      isReturnFromExam: false,
      reuseCount: 0,
      assignedCounterId: 'counter-a',
      assignedCounterName: 'Balcão A',
      callCount: 1,
      createdAt: min(42),
      queuedAt: min(42),
      calledAt: min(36),
      serviceStartedAt: min(35),
    },
    {
      id: 'curr-p-2',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'PARTICULAR',
      subService: 'CONSULTA_PARTICULAR',
      subServiceLabel: 'Marcação de Consulta (Particular)',
      sequenceNumber: 2,
      displayCode: 'P-002',
      status: 'AWAITING_EXAM',
      isReturnFromExam: false,
      reuseCount: 0,
      assignedCounterId: 'counter-a',
      assignedCounterName: 'Balcão A',
      callCount: 1,
      createdAt: min(55),
      queuedAt: min(55),
      calledAt: min(49),
      serviceStartedAt: min(48),
      examStartedAt: min(43),
    },
    {
      id: 'curr-s-1',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'SEGURO',
      subService: 'CONSULTA_SEGURO',
      subServiceLabel: 'Marcação / Elegibilidade Seguro',
      sequenceNumber: 1,
      displayCode: 'S-001',
      status: 'AWAITING_EXAM',
      isReturnFromExam: false,
      reuseCount: 0,
      assignedCounterId: 'counter-b',
      assignedCounterName: 'Balcão B',
      callCount: 1,
      createdAt: min(60),
      queuedAt: min(60),
      calledAt: min(52),
      serviceStartedAt: min(50),
      examStartedAt: min(40),
    },
    {
      id: 'curr-s-2',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'SEGURO',
      subService: 'CONSULTA_SEGURO',
      subServiceLabel: 'Faturação de Guia de Seguro',
      sequenceNumber: 2,
      displayCode: 'S-002',
      status: 'CALLED',
      isReturnFromExam: false,
      reuseCount: 0,
      assignedCounterId: 'counter-b',
      assignedCounterName: 'Balcão B',
      callCount: 1,
      createdAt: min(28),
      queuedAt: min(28),
      calledAt: min(2),
    },
    {
      id: 'curr-p-3',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'PARTICULAR',
      subService: 'CONSULTA_PARTICULAR',
      subServiceLabel: 'Marcação de Consulta (Particular)',
      sequenceNumber: 3,
      displayCode: 'P-003',
      status: 'WAITING',
      isReturnFromExam: false,
      reuseCount: 0,
      callCount: 0,
      createdAt: min(19),
      queuedAt: min(19),
    },
    {
      id: 'curr-s-3',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'SEGURO',
      subService: 'CONSULTA_SEGURO',
      subServiceLabel: 'Faturação de Guia de Seguro',
      sequenceNumber: 3,
      displayCode: 'S-003',
      status: 'WAITING',
      isReturnFromExam: false,
      reuseCount: 0,
      callCount: 0,
      createdAt: min(16),
      queuedAt: min(16),
    },
    {
      id: 'curr-g-1',
      cycleId: currentCycle.id,
      serviceUnitId: 'recepcao',
      category: 'OUTROS',
      subService: 'COTACAO_SIMPLES',
      subServiceLabel: 'Cotação Simples de Exames',
      sequenceNumber: 1,
      displayCode: 'G-001',
      status: 'WAITING',
      isReturnFromExam: false,
      reuseCount: 0,
      callCount: 0,
      createdAt: min(14),
      queuedAt: min(14),
    },
  ];

  return { counters, cycles: [yesterdayCycle, currentCycle], tickets, updatedAt: Date.now() };
}

function loadState(): ServerState {
  try {
    if (fs.existsSync(STATE_FILE)) {
      return JSON.parse(fs.readFileSync(STATE_FILE, 'utf-8'));
    }
  } catch {
    // fallback
  }
  const initial = getInitialState();
  saveState(initial);
  return initial;
}

function saveState(state: ServerState) {
  state.updatedAt = Date.now();
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2), 'utf-8');
}

function sunshineApiPlugin(): Plugin {
  return {
    name: 'sunshine-hospital-api-bridge',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url?.startsWith('/api/')) {
          return next();
        }

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

        if (req.method === 'OPTIONS') {
          res.statusCode = 204;
          res.end();
          return;
        }

        let body = '';
        req.on('data', (chunk) => {
          body += chunk;
        });

        req.on('end', () => {
          const state = loadState();
          const url = req.url!.split('?')[0];
          const currentCycle = state.cycles.find((c) => c.isCurrent) || state.cycles[state.cycles.length - 1];

          const sendJson = (status: number, data: unknown) => {
            res.statusCode = status;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify(data));
          };

          try {
            // GET /api/state -> Used by Web PWA, Android Totem POS & Android Smart TV
            if (req.method === 'GET' && url === '/api/state') {
              return sendJson(200, state);
            }

            // POST /api/sync -> Web PWA pushes state changes
            if (req.method === 'POST' && url === '/api/sync') {
              const payload = JSON.parse(body || '{}');
              if (payload.counters) state.counters = payload.counters;
              if (payload.cycles) state.cycles = payload.cycles;
              if (payload.tickets) state.tickets = payload.tickets;
              saveState(state);
              return sendJson(200, state);
            }

            // POST /api/tickets -> Used by Kotlin Android Totem POS to issue a new ticket
            if (req.method === 'POST' && url === '/api/tickets') {
              const payload = JSON.parse(body || '{}');
              const category: 'PARTICULAR' | 'SEGURO' | 'OUTROS' = payload.category || 'PARTICULAR';
              const subService = payload.subService || 'CONSULTA_PARTICULAR';
              const subServiceLabel = payload.subServiceLabel || 'Atendimento Recepção';

              const prefixMap = { PARTICULAR: 'P', SEGURO: 'S', OUTROS: 'G' };
              const currentTickets = state.tickets.filter((t) => t.cycleId === currentCycle.id && t.category === category);
              const maxSeq = currentTickets.reduce((m, t) => Math.max(m, t.sequenceNumber), 0);
              const nextSeq = maxSeq + 1;
              const displayCode = `${prefixMap[category]}-${String(nextSeq).padStart(3, '0')}`;
              const nowIso = new Date().toISOString();

              const newTicket: Ticket = {
                id: `t-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                cycleId: currentCycle.id,
                serviceUnitId: 'recepcao',
                category,
                subService,
                subServiceLabel,
                sequenceNumber: nextSeq,
                displayCode,
                status: 'WAITING',
                isReturnFromExam: false,
                reuseCount: 0,
                callCount: 0,
                createdAt: nowIso,
                queuedAt: nowIso,
              };

              state.tickets.push(newTicket);
              saveState(state);
              return sendJson(201, newTicket);
            }

            // POST /api/tickets/reuse -> Used by Kotlin Android Totem POS when patient returns from exams
            if (req.method === 'POST' && url === '/api/tickets/reuse') {
              const payload = JSON.parse(body || '{}');
              const rawCode = String(payload.displayCode || '').trim().toUpperCase().replace(/\s+/g, '');
              const formatted = rawCode.includes('-')
                ? rawCode
                : rawCode.replace(/^([A-Z]+)(\d+)$/, (_, p, n) => `${p}-${n.padStart(3, '0')}`);

              const target = state.tickets.find(
                (t) =>
                  (t.displayCode.toUpperCase() === formatted || t.displayCode.toUpperCase() === rawCode) &&
                  t.cycleId === currentCycle.id
              ) || state.tickets.find(
                (t) => t.displayCode.toUpperCase() === formatted || t.displayCode.toUpperCase() === rawCode
              );

              if (!target) {
                return sendJson(404, {
                  ok: false,
                  message: `Senha "${rawCode}" não encontrada no turno atual.`,
                });
              }

              if (target.status === 'WAITING') {
                return sendJson(400, {
                  ok: false,
                  message: `A senha ${target.displayCode} já se encontra na fila de espera.`,
                  ticket: target,
                });
              }

              const nowIso = new Date().toISOString();
              target.cycleId = currentCycle.id;
              target.status = 'WAITING';
              target.isReturnFromExam = true;
              target.reuseCount = (target.reuseCount || 0) + 1;
              target.returnedFromExamAt = nowIso;
              target.queuedAt = nowIso;

              saveState(state);
              return sendJson(200, {
                ok: true,
                message: `Senha ${target.displayCode} reativada com prioridade de Retorno de Exame.`,
                ticket: target,
              });
            }

            return sendJson(404, { error: 'Endpoint not found' });
          } catch (err) {
            return sendJson(500, { error: String(err) });
          }
        });
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), sunshineApiPlugin()],
  server: {
    host: '0.0.0.0',
    port: 5173,
  },
});

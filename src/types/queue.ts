export type QueueCategory = 'PARTICULAR' | 'SEGURO' | 'OUTROS';

export type SubServiceType =
  | 'CONSULTA_PARTICULAR'
  | 'CONSULTA_SEGURO'
  | 'COTACAO_SIMPLES'
  | 'AGENDAMENTO_CONSULTA'
  | 'OUTROS_SERVICOS';

export type TicketStatus =
  | 'WAITING'
  | 'CALLED'
  | 'IN_SERVICE'
  | 'AWAITING_EXAM'
  | 'COMPLETED'
  | 'NO_SHOW';

export interface CounterConfig {
  id: string;
  code: string; // e.g., "A", "B", "C", "D" (editable at any time)
  name: string; // e.g., "Balcão A" or custom name
  subtitle: string;
  serviceUnitId: string;
  primaryQueues: QueueCategory[];
  secondaryQueues: QueueCategory[];
  supportModeActive: boolean; // Whether secondary queue overflow is currently active
  isActive: boolean;
  displayOrder: number;
}

export interface QueueCycle {
  id: string;
  serviceUnitId: string;
  cycleLabel: string;
  isCurrent: boolean;
  startedAt: string;
  closedAt?: string | null;
  resetBy: string;
}

export interface Ticket {
  id: string;
  cycleId: string;
  serviceUnitId: string;
  category: QueueCategory;
  subService: SubServiceType;
  subServiceLabel: string;
  sequenceNumber: number;
  displayCode: string; // e.g. "P-001", "S-001", "G-001"
  status: TicketStatus;
  isReturnFromExam: boolean; // True when patient reused the ticket after medical exams
  reuseCount: number;
  assignedCounterId?: string | null;
  assignedCounterName?: string | null;
  callCount: number;
  createdAt: string;
  queuedAt: string; // Updated when ticket is re-queued after exams
  calledAt?: string | null;
  serviceStartedAt?: string | null;
  examStartedAt?: string | null;
  returnedFromExamAt?: string | null;
  completedAt?: string | null;
}

export interface CategoryMeta {
  id: QueueCategory;
  prefix: string;
  label: string;
  shortLabel: string;
  description: string;
  badgeBg: string;
  badgeText: string;
  accentBorder: string;
  accentColor: string;
}

export const QUEUE_CATEGORY_META: Record<QueueCategory, CategoryMeta> = {
  PARTICULAR: {
    id: 'PARTICULAR',
    prefix: 'P',
    label: 'Atendimento Particular',
    shortLabel: 'Particular',
    description: 'Consultas, exames e faturação para pacientes particulares',
    badgeBg: 'bg-[#FFDAD6]/70',
    badgeText: 'text-[#93000A]',
    accentBorder: 'border-[#B71C1C]',
    accentColor: '#B71C1C',
  },
  SEGURO: {
    id: 'SEGURO',
    prefix: 'S',
    label: 'Pacientes com Seguro',
    shortLabel: 'Seguro',
    description: 'Faturação, elegibilidade e marcação via seguradoras e convénios',
    badgeBg: 'bg-[#C8E6C9]/75',
    badgeText: 'text-[#1B5E20]',
    accentBorder: 'border-[#1B5E20]',
    accentColor: '#1B5E20',
  },
  OUTROS: {
    id: 'OUTROS',
    prefix: 'G',
    label: 'Outros Serviços & Cotações',
    shortLabel: 'Outros Serviços',
    description: 'Cotação simples, agendamento futuro e informações gerais',
    badgeBg: 'bg-[#E2E8F0]',
    badgeText: 'text-[#1E293B]',
    accentBorder: 'border-[#334155]',
    accentColor: '#334155',
  },
};

export const INITIAL_COUNTERS: CounterConfig[] = [
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

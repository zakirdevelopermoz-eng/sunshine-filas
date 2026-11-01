import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import {
  CounterConfig,
  INITIAL_COUNTERS,
  QueueCategory,
  QueueCycle,
  QUEUE_CATEGORY_META,
  SubServiceType,
  Ticket,
  TicketStatus,
} from '../types/queue';
import { getSupabaseClient, getSupabaseCredentials, saveSupabaseCredentials } from '../lib/supabase';

interface CreateTicketInput {
  category: QueueCategory;
  subService: SubServiceType;
  subServiceLabel: string;
}

interface ReuseTicketResult {
  ok: boolean;
  message: string;
  ticket?: Ticket;
}

interface QueueContextValue {
  counters: CounterConfig[];
  cycles: QueueCycle[];
  currentCycle: QueueCycle;
  tickets: Ticket[];
  currentTickets: Ticket[];
  lastCalledTicket: Ticket | null;
  isSupabaseConnected: boolean;
  supabaseUrl: string;
  // Workstation Lock (PWA per Receptionist PC)
  lockedCounterCode: string | null;
  lockWorkstationToCounter: (counterCode: string) => void;
  unlockWorkstation: (adminPin: string) => boolean;
  // Ticket Actions
  createTicket: (input: CreateTicketInput) => Promise<Ticket>;
  callNextForCounter: (counterId: string, preferredCategory?: QueueCategory) => Promise<Ticket | null>;
  callSpecificTicket: (ticketId: string, counterId: string) => Promise<Ticket | null>;
  recallCurrentTicket: (ticketId: string) => Promise<void>;
  updateTicketStatus: (ticketId: string, status: TicketStatus) => Promise<void>;
  reuseTicketByCode: (displayCode: string) => Promise<ReuseTicketResult>;
  // Counter Admin Actions
  updateCounter: (counter: CounterConfig) => Promise<void>;
  addCounter: (input: Omit<CounterConfig, 'id' | 'displayOrder'>) => Promise<void>;
  deleteCounter: (counterId: string) => Promise<void>;
  // Cycle / Reset Actions
  resetDailyCounters: (customLabel?: string) => Promise<void>;
  // Supabase Connection Action
  configureSupabase: (url: string, anonKey: string) => Promise<boolean>;
  resetDemoData: () => void;
}

const QueueContext = createContext<QueueContextValue | undefined>(undefined);

const STORAGE_KEYS = {
  COUNTERS: 'sunshine_v1_counters',
  CYCLES: 'sunshine_v1_cycles',
  TICKETS: 'sunshine_v1_tickets',
  LOCKED_COUNTER: 'sunshine_workstation_locked_counter',
};

const ADMIN_PIN = '1234';
const BROADCAST_CHANNEL_NAME = 'sunshine_hospital_realtime_v1';

export function sortWaitingTickets(list: Ticket[]): Ticket[] {
  return [...list].sort((a, b) => {
    if (a.isReturnFromExam !== b.isReturnFromExam) {
      return a.isReturnFromExam ? -1 : 1;
    }
    return new Date(a.queuedAt).getTime() - new Date(b.queuedAt).getTime();
  });
}

export const QueueProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [counters, setCounters] = useState<CounterConfig[]>(INITIAL_COUNTERS);
  const [cycles, setCycles] = useState<QueueCycle[]>([
    {
      id: 'cycle-active-today',
      serviceUnitId: 'recepcao',
      cycleLabel: `Turno Atual — ${new Date().toLocaleDateString('pt-PT')}`,
      isCurrent: true,
      startedAt: new Date().toISOString(),
      closedAt: null,
      resetBy: 'Sistema Automático',
    },
  ]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [lockedCounterCode, setLockedCounterCode] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_KEYS.LOCKED_COUNTER) || null;
    } catch {
      return null;
    }
  });

  const [isSupabaseConnected, setIsSupabaseConnected] = useState<boolean>(false);
  const [supabaseUrl, setSupabaseUrl] = useState<string>(() => getSupabaseCredentials().url);
  const channelRef = useRef<BroadcastChannel | null>(null);
  const lastServerTimestampRef = useRef<number>(0);

  const lockWorkstationToCounter = useCallback((counterCode: string) => {
    const normalized = counterCode.trim().toUpperCase();
    setLockedCounterCode(normalized);
    try {
      localStorage.setItem(STORAGE_KEYS.LOCKED_COUNTER, normalized);
    } catch {
      // ignore
    }
  }, []);

  const unlockWorkstation = useCallback((pin: string): boolean => {
    if (pin.trim() === ADMIN_PIN) {
      setLockedCounterCode(null);
      try {
        localStorage.removeItem(STORAGE_KEYS.LOCKED_COUNTER);
      } catch {
        // ignore
      }
      return true;
    }
    return false;
  }, []);

  const persistAndBroadcast = useCallback(
    async (nextCounters: CounterConfig[], nextCycles: QueueCycle[], nextTickets: Ticket[]) => {
      try {
        localStorage.setItem(STORAGE_KEYS.COUNTERS, JSON.stringify(nextCounters));
        localStorage.setItem(STORAGE_KEYS.CYCLES, JSON.stringify(nextCycles));
        localStorage.setItem(STORAGE_KEYS.TICKETS, JSON.stringify(nextTickets));
        channelRef.current?.postMessage({
          type: 'STATE_SYNC',
          counters: nextCounters,
          cycles: nextCycles,
          tickets: nextTickets,
        });
      } catch {
        // ignore
      }

      // Push to local API bridge only during local development
      if (typeof window !== 'undefined' && window.location.hostname === 'localhost') {
        try {
          const res = await fetch('/api/sync', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              counters: nextCounters,
              cycles: nextCycles,
              tickets: nextTickets,
            }),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.updatedAt) {
              lastServerTimestampRef.current = data.updatedAt;
            }
          }
        } catch {
          // ignore offline
        }
      }
    },
    []
  );

  // Poll /api/state only on localhost when Supabase is not connected
  useEffect(() => {
    if (isSupabaseConnected) return;
    if (typeof window !== 'undefined' && window.location.hostname !== 'localhost') return;

    let active = true;

    const fetchServerState = async () => {
      try {
        const res = await fetch('/api/state');
        if (!res.ok || !active) return;
        const data = await res.json();
        if (data.updatedAt && data.updatedAt > lastServerTimestampRef.current) {
          lastServerTimestampRef.current = data.updatedAt;
          setCounters(data.counters || INITIAL_COUNTERS);
          setCycles(data.cycles || []);
          setTickets(data.tickets || []);
        }
      } catch {
        // fallback to localStorage if serverless static
      }
    };

    fetchServerState();
    const timer = setInterval(fetchServerState, 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [isSupabaseConnected]);

  // Multi-tab BroadcastChannel
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if ('BroadcastChannel' in window) {
      const bc = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
      bc.onmessage = (event) => {
        if (event.data?.type === 'STATE_SYNC') {
          setCounters(event.data.counters);
          setCycles(event.data.cycles);
          setTickets(event.data.tickets);
        }
      };
      channelRef.current = bc;
    }
    return () => {
      channelRef.current?.close();
    };
  }, []);

  // Supabase Realtime sync (stable reference — no state dependencies to prevent infinite re-fetch loops)
  const isSyncingRef = useRef<boolean>(false);

  const syncWithSupabase = useCallback(async () => {
    const supabase = getSupabaseClient();
    if (!supabase) {
      setIsSupabaseConnected(false);
      return false;
    }

    if (isSyncingRef.current) return true;
    isSyncingRef.current = true;

    try {
      const [countersRes, cyclesRes, ticketsRes] = await Promise.all([
        supabase.from('counters').select('*').order('display_order', { ascending: true }),
        supabase.from('queue_cycles').select('*').order('started_at', { ascending: true }),
        supabase.from('tickets').select('*').order('created_at', { ascending: true }),
      ]);

      if (countersRes.error || cyclesRes.error || ticketsRes.error) {
        setIsSupabaseConnected(false);
        return false;
      }

      setIsSupabaseConnected(true);
      setSupabaseUrl(getSupabaseCredentials().url);

      if (countersRes.data && countersRes.data.length > 0) {
        const mappedCounters: CounterConfig[] = countersRes.data.map((row) => ({
          id: row.id,
          code: row.code,
          name: row.name,
          subtitle: row.subtitle,
          serviceUnitId: row.service_unit_id,
          primaryQueues: row.primary_queues as QueueCategory[],
          secondaryQueues: row.secondary_queues as QueueCategory[],
          supportModeActive: row.support_mode_active,
          isActive: row.is_active,
          displayOrder: row.display_order,
        }));
        setCounters(mappedCounters);
        try {
          localStorage.setItem(STORAGE_KEYS.COUNTERS, JSON.stringify(mappedCounters));
        } catch {
          // ignore
        }
      }

      if (cyclesRes.data && cyclesRes.data.length > 0) {
        const mappedCycles: QueueCycle[] = cyclesRes.data.map((row) => ({
          id: row.id,
          serviceUnitId: row.service_unit_id,
          cycleLabel: row.cycle_label,
          isCurrent: row.is_current,
          startedAt: row.started_at,
          closedAt: row.closed_at,
          resetBy: row.reset_by,
        }));
        setCycles(mappedCycles);
        try {
          localStorage.setItem(STORAGE_KEYS.CYCLES, JSON.stringify(mappedCycles));
        } catch {
          // ignore
        }
      }

      if (ticketsRes.data) {
        const mappedTickets: Ticket[] = ticketsRes.data.map((row) => ({
          id: row.id,
          cycleId: row.cycle_id,
          serviceUnitId: row.service_unit_id,
          category: row.category as QueueCategory,
          subService: row.sub_service as SubServiceType,
          subServiceLabel: row.sub_service_label,
          sequenceNumber: row.sequence_number,
          displayCode: row.display_code,
          status: row.status as TicketStatus,
          isReturnFromExam: row.is_return_from_exam,
          reuseCount: row.reuse_count,
          assignedCounterId: row.assigned_counter_id,
          assignedCounterName: row.assigned_counter_name,
          callCount: row.call_count,
          createdAt: row.created_at,
          queuedAt: row.queued_at,
          calledAt: row.called_at,
          serviceStartedAt: row.service_started_at,
          examStartedAt: row.exam_started_at,
          returnedFromExamAt: row.returned_from_exam_at,
          completedAt: row.completed_at,
        }));
        setTickets(mappedTickets);
        try {
          localStorage.setItem(STORAGE_KEYS.TICKETS, JSON.stringify(mappedTickets));
        } catch {
          // ignore
        }
      }

      return true;
    } catch {
      setIsSupabaseConnected(false);
      return false;
    } finally {
      isSyncingRef.current = false;
    }
  }, []);

  useEffect(() => {
    syncWithSupabase();
    const supabase = getSupabaseClient();
    if (!supabase) return;

    const channel = supabase
      .channel('sunshine-db-changes')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'tickets' }, () => {
        syncWithSupabase();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'counters' }, () => {
        syncWithSupabase();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'queue_cycles' }, () => {
        syncWithSupabase();
      })
      .subscribe();

    // Gentle 3-second polling backup in case WebSocket drops
    const pollInterval = setInterval(() => {
      syncWithSupabase();
    }, 3000);

    return () => {
      clearInterval(pollInterval);
      supabase.removeChannel(channel);
    };
  }, [syncWithSupabase]);

  const currentCycle =
    cycles.find((c) => c.isCurrent) ||
    cycles[cycles.length - 1] || {
      id: 'cycle-default',
      serviceUnitId: 'recepcao',
      cycleLabel: 'Turno Atual',
      isCurrent: true,
      startedAt: new Date().toISOString(),
      resetBy: 'Sistema',
    };

  const currentTickets = tickets.filter((t) => t.cycleId === currentCycle.id);

  const lastCalledTicket =
    [...currentTickets]
      .filter((t) => (t.status === 'CALLED' || t.status === 'IN_SERVICE') && t.calledAt)
      .sort((a, b) => new Date(b.calledAt!).getTime() - new Date(a.calledAt!).getTime())[0] || null;

  const createTicket = useCallback(
    async (input: CreateTicketInput): Promise<Ticket> => {
      const categoryTickets = currentTickets.filter((t) => t.category === input.category);
      const maxSeq = categoryTickets.reduce((max, t) => Math.max(max, t.sequenceNumber), 0);
      const nextSeq = maxSeq + 1;
      const prefix = QUEUE_CATEGORY_META[input.category].prefix;
      const displayCode = `${prefix}-${String(nextSeq).padStart(3, '0')}`;
      const nowIso = new Date().toISOString();

      const newTicket: Ticket = {
        id: crypto.randomUUID ? crypto.randomUUID() : `t-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        cycleId: currentCycle.id,
        serviceUnitId: 'recepcao',
        category: input.category,
        subService: input.subService,
        subServiceLabel: input.subServiceLabel,
        sequenceNumber: nextSeq,
        displayCode,
        status: 'WAITING',
        isReturnFromExam: false,
        reuseCount: 0,
        callCount: 0,
        createdAt: nowIso,
        queuedAt: nowIso,
      };

      const nextTickets = [...tickets, newTicket];
      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        await supabase.from('tickets').insert({
          id: newTicket.id,
          cycle_id: newTicket.cycleId,
          service_unit_id: newTicket.serviceUnitId,
          category: newTicket.category,
          sub_service: newTicket.subService,
          sub_service_label: newTicket.subServiceLabel,
          sequence_number: newTicket.sequenceNumber,
          display_code: newTicket.displayCode,
          status: newTicket.status,
          is_return_from_exam: false,
          reuse_count: 0,
          call_count: 0,
          created_at: nowIso,
          queued_at: nowIso,
        });
      }

      return newTicket;
    },
    [currentTickets, currentCycle.id, tickets, counters, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const callNextForCounter = useCallback(
    async (counterId: string, preferredCategory?: QueueCategory): Promise<Ticket | null> => {
      const counter = counters.find((c) => c.id === counterId);
      if (!counter) return null;

      // Enforce Workstation Counter Lock: A PC locked to Counter A can NEVER call patients for Counter B
      if (lockedCounterCode && counter.code.toUpperCase() !== lockedCounterCode.toUpperCase()) {
        throw new Error(
          `Bloqueio de Segurança: Este computador está designado exclusivamente ao Balcão ${lockedCounterCode} e não pode chamar pacientes para o ${counter.name}.`
        );
      }

      const waitingInCycle = currentTickets.filter((t) => t.status === 'WAITING');
      let candidate: Ticket | undefined;

      if (preferredCategory) {
        const pool = sortWaitingTickets(waitingInCycle.filter((t) => t.category === preferredCategory));
        candidate = pool[0];
      } else {
        const primaryPool = sortWaitingTickets(
          waitingInCycle.filter((t) => counter.primaryQueues.includes(t.category))
        );
        candidate = primaryPool[0];

        if (!candidate && counter.supportModeActive && counter.secondaryQueues.length > 0) {
          const secondaryPool = sortWaitingTickets(
            waitingInCycle.filter((t) => counter.secondaryQueues.includes(t.category))
          );
          candidate = secondaryPool[0];
        }
      }

      if (!candidate) return null;

      const nowIso = new Date().toISOString();

      const nextTickets = tickets.map((t) => {
        if (t.id === candidate!.id) {
          return {
            ...t,
            status: 'CALLED' as TicketStatus,
            assignedCounterId: counter.id,
            assignedCounterName: counter.name,
            callCount: t.callCount + 1,
            calledAt: nowIso,
          };
        }
        if (
          t.cycleId === currentCycle.id &&
          t.assignedCounterId === counter.id &&
          (t.status === 'CALLED' || t.status === 'IN_SERVICE')
        ) {
          return {
            ...t,
            status: 'COMPLETED' as TicketStatus,
            completedAt: nowIso,
          };
        }
        return t;
      });

      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const updatedCandidate = nextTickets.find((t) => t.id === candidate!.id) || null;

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected && updatedCandidate) {
        await supabase
          .from('tickets')
          .update({
            status: 'CALLED',
            assigned_counter_id: counter.id,
            assigned_counter_name: counter.name,
            call_count: updatedCandidate.callCount,
            called_at: nowIso,
          })
          .eq('id', updatedCandidate.id);
      }

      return updatedCandidate;
    },
    [counters, lockedCounterCode, currentTickets, tickets, currentCycle.id, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const callSpecificTicket = useCallback(
    async (ticketId: string, counterId: string): Promise<Ticket | null> => {
      const counter = counters.find((c) => c.id === counterId);
      if (!counter) return null;

      if (lockedCounterCode && counter.code.toUpperCase() !== lockedCounterCode.toUpperCase()) {
        throw new Error(
          `Bloqueio de Segurança: Este terminal está vinculado ao Balcão ${lockedCounterCode}.`
        );
      }

      const nowIso = new Date().toISOString();
      let calledTicket: Ticket | null = null;

      const nextTickets = tickets.map((t) => {
        if (t.id === ticketId) {
          calledTicket = {
            ...t,
            status: 'CALLED',
            assignedCounterId: counter.id,
            assignedCounterName: counter.name,
            callCount: t.callCount + 1,
            calledAt: nowIso,
          };
          return calledTicket;
        }
        if (
          t.cycleId === currentCycle.id &&
          t.assignedCounterId === counter.id &&
          (t.status === 'CALLED' || t.status === 'IN_SERVICE')
        ) {
          return {
            ...t,
            status: 'COMPLETED' as TicketStatus,
            completedAt: nowIso,
          };
        }
        return t;
      });

      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected && calledTicket) {
        await supabase
          .from('tickets')
          .update({
            status: 'CALLED',
            assigned_counter_id: counter.id,
            assigned_counter_name: counter.name,
            call_count: (calledTicket as Ticket).callCount,
            called_at: nowIso,
          })
          .eq('id', ticketId);
      }

      return calledTicket;
    },
    [counters, lockedCounterCode, tickets, currentCycle.id, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const recallCurrentTicket = useCallback(
    async (ticketId: string): Promise<void> => {
      const nowIso = new Date().toISOString();
      const nextTickets = tickets.map((t) =>
        t.id === ticketId
          ? {
              ...t,
              status: 'CALLED' as TicketStatus,
              callCount: t.callCount + 1,
              calledAt: nowIso,
            }
          : t
      );
      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const target = nextTickets.find((t) => t.id === ticketId);
      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected && target) {
        await supabase
          .from('tickets')
          .update({
            status: 'CALLED',
            call_count: target.callCount,
            called_at: nowIso,
          })
          .eq('id', ticketId);
      }
    },
    [tickets, counters, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const updateTicketStatus = useCallback(
    async (ticketId: string, status: TicketStatus): Promise<void> => {
      const nowIso = new Date().toISOString();
      const nextTickets = tickets.map((t) => {
        if (t.id !== ticketId) return t;
        return {
          ...t,
          status,
          serviceStartedAt: status === 'IN_SERVICE' ? nowIso : t.serviceStartedAt,
          examStartedAt: status === 'AWAITING_EXAM' ? nowIso : t.examStartedAt,
          completedAt: status === 'COMPLETED' || status === 'NO_SHOW' ? nowIso : t.completedAt,
        };
      });

      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        const patch: Record<string, unknown> = { status };
        if (status === 'IN_SERVICE') patch.service_started_at = nowIso;
        if (status === 'AWAITING_EXAM') patch.exam_started_at = nowIso;
        if (status === 'COMPLETED' || status === 'NO_SHOW') patch.completed_at = nowIso;
        await supabase.from('tickets').update(patch).eq('id', ticketId);
      }
    },
    [tickets, counters, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const reuseTicketByCode = useCallback(
    async (rawCode: string): Promise<ReuseTicketResult> => {
      const normalized = rawCode.trim().toUpperCase().replace(/\s+/g, '');
      const formatted = normalized.includes('-')
        ? normalized
        : normalized.replace(/^([A-Z]+)(\d+)$/, (_, p, n) => `${p}-${n.padStart(3, '0')}`);

      const target =
        currentTickets.find(
          (t) => t.displayCode.toUpperCase() === formatted || t.displayCode.toUpperCase() === normalized
        ) ||
        tickets.find(
          (t) => t.displayCode.toUpperCase() === formatted || t.displayCode.toUpperCase() === normalized
        );

      if (!target) {
        return {
          ok: false,
          message: `Senha "${rawCode.toUpperCase()}" não encontrada no turno atual.`,
        };
      }

      if (target.status === 'WAITING') {
        return {
          ok: false,
          message: `A senha ${target.displayCode} já se encontra na fila de espera.`,
          ticket: target,
        };
      }

      if (target.status === 'CALLED' || target.status === 'IN_SERVICE') {
        return {
          ok: false,
          message: `A senha ${target.displayCode} está em atendimento ativo no ${target.assignedCounterName || 'balcão'}.`,
          ticket: target,
        };
      }

      const nowIso = new Date().toISOString();
      let updatedTicket: Ticket | undefined;

      const nextTickets = tickets.map((t) => {
        if (t.id === target.id) {
          updatedTicket = {
            ...t,
            cycleId: currentCycle.id,
            status: 'WAITING',
            isReturnFromExam: true,
            reuseCount: t.reuseCount + 1,
            returnedFromExamAt: nowIso,
            queuedAt: nowIso,
          };
          return updatedTicket;
        }
        return t;
      });

      setTickets(nextTickets);
      await persistAndBroadcast(counters, cycles, nextTickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected && updatedTicket) {
        await supabase
          .from('tickets')
          .update({
            cycle_id: currentCycle.id,
            status: 'WAITING',
            is_return_from_exam: true,
            reuse_count: updatedTicket.reuseCount,
            returned_from_exam_at: nowIso,
            queued_at: nowIso,
          })
          .eq('id', updatedTicket.id);
      }

      return {
        ok: true,
        message: `Senha ${target.displayCode} reativada com prioridade de Retorno de Exame.`,
        ticket: updatedTicket,
      };
    },
    [currentTickets, tickets, currentCycle.id, counters, cycles, persistAndBroadcast, isSupabaseConnected]
  );

  const updateCounter = useCallback(
    async (updated: CounterConfig): Promise<void> => {
      const nextCounters = counters.map((c) => (c.id === updated.id ? updated : c));
      setCounters(nextCounters);
      await persistAndBroadcast(nextCounters, cycles, tickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        await supabase
          .from('counters')
          .update({
            code: updated.code,
            name: updated.name,
            subtitle: updated.subtitle,
            primary_queues: updated.primaryQueues,
            secondary_queues: updated.secondaryQueues,
            support_mode_active: updated.supportModeActive,
            is_active: updated.isActive,
            display_order: updated.displayOrder,
            updated_at: new Date().toISOString(),
          })
          .eq('id', updated.id);
      }
    },
    [counters, cycles, tickets, persistAndBroadcast, isSupabaseConnected]
  );

  const addCounter = useCallback(
    async (input: Omit<CounterConfig, 'id' | 'displayOrder'>): Promise<void> => {
      const nextOrder = counters.reduce((max, c) => Math.max(max, c.displayOrder), 0) + 1;
      const newCounter: CounterConfig = {
        ...input,
        id: `counter-${input.code.toLowerCase()}-${Date.now().toString(36)}`,
        displayOrder: nextOrder,
      };
      const nextCounters = [...counters, newCounter];
      setCounters(nextCounters);
      await persistAndBroadcast(nextCounters, cycles, tickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        await supabase.from('counters').insert({
          id: newCounter.id,
          code: newCounter.code,
          name: newCounter.name,
          subtitle: newCounter.subtitle,
          service_unit_id: newCounter.serviceUnitId,
          primary_queues: newCounter.primaryQueues,
          secondary_queues: newCounter.secondaryQueues,
          support_mode_active: newCounter.supportModeActive,
          is_active: newCounter.isActive,
          display_order: newCounter.displayOrder,
        });
      }
    },
    [counters, cycles, tickets, persistAndBroadcast, isSupabaseConnected]
  );

  const deleteCounter = useCallback(
    async (counterId: string): Promise<void> => {
      const nextCounters = counters.filter((c) => c.id !== counterId);
      setCounters(nextCounters);
      await persistAndBroadcast(nextCounters, cycles, tickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        await supabase.from('counters').delete().eq('id', counterId);
      }
    },
    [counters, cycles, tickets, persistAndBroadcast, isSupabaseConnected]
  );

  const resetDailyCounters = useCallback(
    async (customLabel?: string): Promise<void> => {
      const nowIso = new Date().toISOString();
      const newCycle: QueueCycle = {
        id: crypto.randomUUID ? crypto.randomUUID() : `cycle-${Date.now()}`,
        serviceUnitId: 'recepcao',
        cycleLabel:
          customLabel?.trim() ||
          `Turno Iniciado — ${new Date().toLocaleDateString('pt-PT')} às ${new Date().toLocaleTimeString('pt-PT', {
            hour: '2-digit',
            minute: '2-digit',
          })}`,
        isCurrent: true,
        startedAt: nowIso,
        closedAt: null,
        resetBy: 'Admin CRM',
      };

      const nextCycles = cycles.map((c) => (c.isCurrent ? { ...c, isCurrent: false, closedAt: nowIso } : c));
      nextCycles.push(newCycle);

      setCycles(nextCycles);
      await persistAndBroadcast(counters, nextCycles, tickets);

      const supabase = getSupabaseClient();
      if (supabase && isSupabaseConnected) {
        await supabase.from('queue_cycles').update({ is_current: false, closed_at: nowIso }).eq('is_current', true);
        await supabase.from('queue_cycles').insert({
          id: newCycle.id,
          service_unit_id: newCycle.serviceUnitId,
          cycle_label: newCycle.cycleLabel,
          is_current: true,
          started_at: nowIso,
          reset_by: newCycle.resetBy,
        });
      }
    },
    [cycles, counters, tickets, persistAndBroadcast, isSupabaseConnected]
  );

  const configureSupabase = useCallback(
    async (url: string, anonKey: string): Promise<boolean> => {
      saveSupabaseCredentials(url, anonKey);
      setSupabaseUrl(url);
      if (!url || !anonKey) {
        setIsSupabaseConnected(false);
        return false;
      }
      return await syncWithSupabase();
    },
    [syncWithSupabase]
  );

  const resetDemoData = useCallback(() => {
    localStorage.removeItem(STORAGE_KEYS.COUNTERS);
    localStorage.removeItem(STORAGE_KEYS.CYCLES);
    localStorage.removeItem(STORAGE_KEYS.TICKETS);
    window.location.reload();
  }, []);

  return (
    <QueueContext.Provider
      value={{
        counters,
        cycles,
        currentCycle,
        tickets,
        currentTickets,
        lastCalledTicket,
        isSupabaseConnected,
        supabaseUrl,
        lockedCounterCode,
        lockWorkstationToCounter,
        unlockWorkstation,
        createTicket,
        callNextForCounter,
        callSpecificTicket,
        recallCurrentTicket,
        updateTicketStatus,
        reuseTicketByCode,
        updateCounter,
        addCounter,
        deleteCounter,
        resetDailyCounters,
        configureSupabase,
        resetDemoData,
      }}
    >
      {children}
    </QueueContext.Provider>
  );
};

export function useQueue(): QueueContextValue {
  const ctx = useContext(QueueContext);
  if (!ctx) {
    throw new Error('useQueue must be used inside QueueProvider');
  }
  return ctx;
}

import React, { useState, useEffect } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  CheckCircle2,
  Clock,
  FlaskConical,
  Play,
  RotateCcw,
  SkipForward,
  UserCheck,
  UserX,
  ArrowLeftRight,
  Volume2,
} from 'lucide-react';
import { useQueue, sortWaitingTickets } from '../context/QueueContext';
import { QUEUE_CATEGORY_META, QueueCategory, Ticket } from '../types/queue';

export const RecepcaoPage: React.FC = () => {
  const { counterCode: routeCounterCode } = useParams<{ counterCode?: string }>();
  const [searchParams] = useSearchParams();
  const queryCounterCode = searchParams.get('balcao');

  const {
    counters,
    currentTickets,
    lockedCounterCode,
    lockWorkstationToCounter,
    callNextForCounter,
    callSpecificTicket,
    recallCurrentTicket,
    updateTicketStatus,
    reuseTicketByCode,
    updateCounter,
  } = useQueue();

  const activeCounters = counters.filter((c) => c.isActive);

  // Auto-bind workstation if URL has /recepcao/A or ?balcao=A
  useEffect(() => {
    const targetCode = (routeCounterCode || queryCounterCode)?.trim().toUpperCase();
    if (targetCode && activeCounters.some((c) => c.code.toUpperCase() === targetCode)) {
      lockWorkstationToCounter(targetCode);
    }
  }, [routeCounterCode, queryCounterCode, activeCounters, lockWorkstationToCounter]);

  const effectiveCounter = lockedCounterCode
    ? activeCounters.find((c) => c.code.toUpperCase() === lockedCounterCode.toUpperCase()) ||
      activeCounters[0]
    : null;

  const [selectedCounterId, setSelectedCounterId] = useState<string>(
    effectiveCounter?.id || activeCounters[0]?.id || 'counter-a'
  );

  useEffect(() => {
    if (effectiveCounter) {
      setSelectedCounterId(effectiveCounter.id);
    } else if (!activeCounters.some((c) => c.id === selectedCounterId) && activeCounters[0]) {
      setSelectedCounterId(activeCounters[0].id);
    }
  }, [effectiveCounter, activeCounters, selectedCounterId]);

  const currentCounter =
    effectiveCounter ||
    activeCounters.find((c) => c.id === selectedCounterId) ||
    activeCounters[0];

  const activeTicketAtCounter =
    currentTickets.find(
      (t) =>
        t.assignedCounterId === currentCounter?.id &&
        (t.status === 'CALLED' || t.status === 'IN_SERVICE')
    ) || null;

  const waitingTickets = sortWaitingTickets(
    currentTickets.filter((t) => t.status === 'WAITING')
  );

  const examHoldTickets = currentTickets.filter((t) => t.status === 'AWAITING_EXAM');

  const primaryWaiting = waitingTickets.filter((t) =>
    currentCounter?.primaryQueues.includes(t.category)
  );
  const secondaryWaiting = waitingTickets.filter((t) =>
    currentCounter?.secondaryQueues.includes(t.category)
  );

  const handleCallNext = async (preferredCategory?: QueueCategory) => {
    if (!currentCounter) return;
    await callNextForCounter(currentCounter.id, preferredCategory);
  };

  const handleRecall = async (ticket: Ticket) => {
    await recallCurrentTicket(ticket.id);
  };

  const handleCallSpecific = async (ticket: Ticket) => {
    if (!currentCounter) return;
    await callSpecificTicket(ticket.id, currentCounter.id);
  };

  const toggleSupportMode = async () => {
    if (!currentCounter) return;
    await updateCounter({
      ...currentCounter,
      supportModeActive: !currentCounter.supportModeActive,
    });
  };

  const getWaitMinutes = (iso: string) => {
    const diff = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000));
    return diff === 0 ? 'Agora' : `${diff} min`;
  };

  if (!currentCounter) {
    return (
      <div className="p-12 text-center text-sm text-[#5C5858]">
        Nenhum balcão ativo configurado. Aceda ao painel Admin CRM no menu lateral para ativar balcões.
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1600px] flex-1 px-4 py-6 sm:px-6 space-y-5">
      {/* Compact Counter Tabs only when PC is not locked (assignment is managed in Hamburger Menu) */}
      {!lockedCounterCode && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#E5DFDE] bg-white px-4 py-2.5 shadow-2xs">
          <span className="text-xs font-bold text-[#5C5858]">
            Visualização de Balcão (Para fixar este PC a um balcão, abra o Menu no topo direito):
          </span>
          <div className="flex flex-wrap items-center gap-1.5">
            {activeCounters.map((counter) => {
              const isSelected = counter.id === currentCounter.id;
              return (
                <button
                  key={counter.id}
                  onClick={() => setSelectedCounterId(counter.id)}
                  className={`rounded-xl px-3.5 py-1.5 text-xs font-bold transition-all ${
                    isSelected
                      ? 'bg-[#B71C1C] text-white shadow-2xs'
                      : 'bg-[#F5F2F2] text-[#4A4646] hover:bg-[#EAE4E3]'
                  }`}
                >
                  {counter.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Main Workspace Grid */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
        {/* LEFT COLUMN (5 cols): Active Counter Controls & Current Patient */}
        <div className="xl:col-span-5 space-y-6">
          <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs space-y-5">
            <div className="flex items-start justify-between gap-4 border-b border-[#EFE9E8] pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="rounded-lg bg-[#B71C1C] px-2.5 py-1 font-mono-code text-xs font-bold text-white">
                    {currentCounter.code}
                  </span>
                  <h2 className="text-2xl font-extrabold text-[#1C1B1B]">
                    {currentCounter.name}
                  </h2>
                </div>
                <p className="mt-1 text-xs font-medium text-[#5C5858]">
                  {currentCounter.subtitle}
                </p>
              </div>

              {currentCounter.secondaryQueues.length > 0 && (
                <button
                  onClick={toggleSupportMode}
                  className={`flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-xs font-bold transition-all duration-300 ${
                    currentCounter.supportModeActive
                      ? 'border-[#A5D6A7] bg-[#E8F5E9] text-[#1B5E20]'
                      : 'border-[#E5DFDE] bg-[#F5F2F2] text-[#5C5858]'
                  }`}
                >
                  <ArrowLeftRight className="h-3.5 w-3.5" />
                  <span>
                    {currentCounter.supportModeActive
                      ? 'Apoio Secundário: Ativo'
                      : 'Apoio Secundário: Inativo'}
                  </span>
                </button>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-3.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold uppercase tracking-wider text-[#5C5858]">
                    Fila Principal
                  </span>
                  <span className="font-mono-code font-bold text-[#B71C1C]">
                    {primaryWaiting.length} em espera
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {currentCounter.primaryQueues.map((q) => (
                    <span
                      key={q}
                      className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${QUEUE_CATEGORY_META[q].badgeBg} ${QUEUE_CATEGORY_META[q].badgeText}`}
                    >
                      {QUEUE_CATEGORY_META[q].shortLabel}
                    </span>
                  ))}
                </div>
              </div>

              <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-3.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold uppercase tracking-wider text-[#5C5858]">
                    Fila de Apoio (Transbordo)
                  </span>
                  <span className="font-mono-code font-bold text-[#1B5E20]">
                    {secondaryWaiting.length} em espera
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {currentCounter.secondaryQueues.length === 0 ? (
                    <span className="text-xs text-[#787272]">Sem fila secundária</span>
                  ) : (
                    currentCounter.secondaryQueues.map((q) => (
                      <span
                        key={q}
                        className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${QUEUE_CATEGORY_META[q].badgeBg} ${QUEUE_CATEGORY_META[q].badgeText}`}
                      >
                        {QUEUE_CATEGORY_META[q].shortLabel}
                      </span>
                    ))
                  )}
                </div>
              </div>
            </div>

            <div className="space-y-2.5 pt-1">
              <button
                onClick={() => handleCallNext()}
                disabled={
                  primaryWaiting.length === 0 &&
                  (!currentCounter.supportModeActive || secondaryWaiting.length === 0)
                }
                className="w-full flex items-center justify-between rounded-2xl bg-[#B71C1C] px-6 py-4 text-sm font-bold text-white shadow-sm transition-all duration-300 hover:bg-[#93000A] disabled:opacity-40 disabled:pointer-events-none"
              >
                <div className="flex items-center gap-3">
                  <SkipForward className="h-5 w-5" />
                  <div className="text-left">
                    <div>Chamar Próxima Senha para {currentCounter.name}</div>
                    <div className="text-[11px] font-normal text-white/80">
                      Notifica instantaneamente a Smart TV ({primaryWaiting.length} na fila principal)
                    </div>
                  </div>
                </div>
                <span className="rounded-full bg-white/20 px-3 py-1 font-mono-code text-xs">
                  {currentCounter.code}
                </span>
              </button>

              {currentCounter.secondaryQueues.length > 0 && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {currentCounter.secondaryQueues.map((secCat) => {
                    const count = waitingTickets.filter((t) => t.category === secCat).length;
                    return (
                      <button
                        key={secCat}
                        disabled={count === 0}
                        onClick={() => handleCallNext(secCat)}
                        className="flex items-center justify-between rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-4 py-2.5 text-xs font-bold text-[#1C1B1B] transition-all hover:border-[#1B5E20] hover:bg-[#E8F5E9] hover:text-[#1B5E20] disabled:opacity-40 disabled:pointer-events-none"
                      >
                        <span>
                          Apoiar Fila {QUEUE_CATEGORY_META[secCat].shortLabel}
                        </span>
                        <span className="font-mono-code rounded-full bg-white px-2 py-0.5 text-[11px] border border-[#E5DFDE]">
                          {count}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Active Ticket Card */}
          <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
            <div className="flex items-center justify-between border-b border-[#EFE9E8] pb-4">
              <span className="text-xs font-bold uppercase tracking-wider text-[#5C5858]">
                Senha em Atendimento no {currentCounter.name}
              </span>
              {activeTicketAtCounter && (
                <span
                  className={`rounded-full px-3 py-0.5 text-xs font-bold ${
                    activeTicketAtCounter.status === 'CALLED'
                      ? 'bg-[#FFDAD6] text-[#93000A] animate-pulse'
                      : 'bg-[#C8E6C9] text-[#1B5E20]'
                  }`}
                >
                  {activeTicketAtCounter.status === 'CALLED'
                    ? 'A Chamar na Smart TV'
                    : 'Em Atendimento'}
                </span>
              )}
            </div>

            {!activeTicketAtCounter ? (
              <div className="py-12 text-center space-y-3">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#F5F2F2] text-[#787272]">
                  <UserCheck className="h-6 w-6" />
                </div>
                <p className="text-sm font-bold text-[#1C1B1B]">
                  {currentCounter.name} Livre neste momento
                </p>
                <p className="text-xs text-[#6E6868] max-w-xs mx-auto">
                  Clique em &ldquo;Chamar Próxima Senha&rdquo; acima para anunciar o próximo paciente na Smart TV.
                </p>
              </div>
            ) : (
              <div className="pt-5 space-y-6 animate-fade-slide-up">
                <div className="flex items-center justify-between rounded-2xl bg-[#FCF9F9] border border-[#EFE9E8] p-5">
                  <div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-[11px] font-bold ${
                          QUEUE_CATEGORY_META[activeTicketAtCounter.category].badgeBg
                        } ${QUEUE_CATEGORY_META[activeTicketAtCounter.category].badgeText}`}
                      >
                        {QUEUE_CATEGORY_META[activeTicketAtCounter.category].shortLabel}
                      </span>
                      {activeTicketAtCounter.isReturnFromExam && (
                        <span className="rounded-full bg-[#1B5E20] px-2.5 py-0.5 text-[10px] font-bold uppercase text-white">
                          Retorno de Exame ({activeTicketAtCounter.reuseCount}x)
                        </span>
                      )}
                    </div>
                    <div className="mt-2 font-mono-code text-5xl font-extrabold tracking-tight text-[#1C1B1B]">
                      {activeTicketAtCounter.displayCode}
                    </div>
                    <p className="mt-1 text-xs font-medium text-[#5C5858]">
                      {activeTicketAtCounter.subServiceLabel}
                    </p>
                  </div>

                  <div className="text-right space-y-2">
                    <button
                      onClick={() => handleRecall(activeTicketAtCounter)}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-[#B71C1C]/30 bg-[#FFF5F5] px-3.5 py-2 text-xs font-bold text-[#B71C1C] transition-all hover:bg-[#B71C1C] hover:text-white"
                    >
                      <Volume2 className="h-4 w-4" />
                      <span>Rechamar na Smart TV ({activeTicketAtCounter.callCount}x)</span>
                    </button>
                    <p className="text-[11px] text-[#787272]">
                      Emitida às{' '}
                      {new Date(activeTicketAtCounter.createdAt).toLocaleTimeString('pt-PT', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {activeTicketAtCounter.status === 'CALLED' && (
                    <button
                      onClick={() =>
                        updateTicketStatus(activeTicketAtCounter.id, 'IN_SERVICE')
                      }
                      className="sm:col-span-2 flex items-center justify-center gap-2 rounded-2xl bg-[#1B5E20] py-3.5 text-xs font-bold text-white shadow-xs transition-all hover:bg-[#144718]"
                    >
                      <Play className="h-4 w-4" />
                      <span>Confirmar Presença & Iniciar Atendimento</span>
                    </button>
                  )}

                  <button
                    onClick={() =>
                      updateTicketStatus(activeTicketAtCounter.id, 'AWAITING_EXAM')
                    }
                    className="flex items-center justify-center gap-2 rounded-2xl border-2 border-[#1B5E20] bg-[#E8F5E9] py-3.5 px-4 text-xs font-bold text-[#1B5E20] transition-all hover:bg-[#1B5E20] hover:text-white"
                  >
                    <FlaskConical className="h-4 w-4" />
                    <span>Aguardar Exame (Reutilizar no Totem)</span>
                  </button>

                  <button
                    onClick={() =>
                      updateTicketStatus(activeTicketAtCounter.id, 'COMPLETED')
                    }
                    className="flex items-center justify-center gap-2 rounded-2xl bg-[#1C1B1B] py-3.5 px-4 text-xs font-bold text-white transition-all hover:bg-[#333030]"
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    <span>Concluir Atendimento</span>
                  </button>

                  <button
                    onClick={() =>
                      updateTicketStatus(activeTicketAtCounter.id, 'NO_SHOW')
                    }
                    className="sm:col-span-2 flex items-center justify-center gap-1.5 rounded-xl border border-[#E5DFDE] py-2.5 text-xs font-semibold text-[#6E6868] transition-all hover:border-[#B71C1C] hover:text-[#B71C1C]"
                  >
                    <UserX className="h-3.5 w-3.5" />
                    <span>Paciente Ausente (Não Compareceu)</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* RIGHT COLUMN (7 cols): Live Queues by Category + Patients in Exam Hold */}
        <div className="xl:col-span-7 space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {(['PARTICULAR', 'SEGURO', 'OUTROS'] as QueueCategory[]).map((cat) => {
              const meta = QUEUE_CATEGORY_META[cat];
              const list = waitingTickets.filter((t) => t.category === cat);
              const isPrimaryForSelected = currentCounter.primaryQueues.includes(cat);
              const isSecondaryForSelected = currentCounter.secondaryQueues.includes(cat);

              return (
                <div
                  key={cat}
                  className={`flex flex-col rounded-[26px] border bg-white p-4 shadow-2xs transition-all ${
                    isPrimaryForSelected
                      ? 'border-[#B71C1C]/50 ring-1 ring-[#B71C1C]/20'
                      : 'border-[#E5DFDE]'
                  }`}
                >
                  <div className="flex items-center justify-between border-b border-[#EFE9E8] pb-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span
                          className={`rounded-lg px-2 py-0.5 font-mono-code text-xs font-extrabold ${meta.badgeBg} ${meta.badgeText}`}
                        >
                          {meta.prefix}
                        </span>
                        <h3 className="text-sm font-extrabold text-[#1C1B1B]">
                          {meta.shortLabel}
                        </h3>
                      </div>
                      <p className="mt-1 text-[11px] font-medium text-[#6E6868]">
                        {isPrimaryForSelected
                          ? `Fila Principal do ${currentCounter.name}`
                          : isSecondaryForSelected
                          ? `Fila de Apoio do ${currentCounter.name}`
                          : 'Outras Filas'}
                      </p>
                    </div>

                    <span className="font-mono-code rounded-full bg-[#F5F2F2] px-2.5 py-1 text-xs font-bold text-[#1C1B1B]">
                      {list.length}
                    </span>
                  </div>

                  <div className="mt-3 flex-1 space-y-2.5 max-h-[360px] overflow-y-auto pr-1">
                    {list.length === 0 ? (
                      <div className="py-10 text-center text-xs text-[#8C8585]">
                        Nenhum paciente em espera nesta fila
                      </div>
                    ) : (
                      list.map((ticket, index) => (
                        <div
                          key={ticket.id}
                          className={`group flex items-center justify-between rounded-2xl border p-3 transition-all duration-200 ${
                            ticket.isReturnFromExam
                              ? 'border-[#A5D6A7] bg-[#F1F8E9]'
                              : index === 0
                              ? 'border-[#E5DFDE] bg-[#FCF9F9]'
                              : 'border-[#EFE9E8] bg-white hover:border-[#DCD4D3]'
                          }`}
                        >
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-mono-code text-base font-extrabold text-[#1C1B1B]">
                                {ticket.displayCode}
                              </span>
                              {ticket.isReturnFromExam && (
                                <span className="inline-flex items-center gap-1 rounded-md bg-[#1B5E20] px-1.5 py-0.5 text-[10px] font-bold text-white">
                                  <RotateCcw className="h-2.5 w-2.5" />
                                  Retorno Exame
                                </span>
                              )}
                            </div>
                            <p className="text-[11px] text-[#5C5858] line-clamp-1">
                              {ticket.subServiceLabel}
                            </p>
                            <div className="mt-1 flex items-center gap-1 text-[10px] font-medium text-[#787272]">
                              <Clock className="h-3 w-3" />
                              <span>Espera: {getWaitMinutes(ticket.queuedAt)}</span>
                            </div>
                          </div>

                          <button
                            onClick={() => handleCallSpecific(ticket)}
                            title={`Chamar ${ticket.displayCode} para ${currentCounter.name}`}
                            className="rounded-xl border border-[#DCD4D3] bg-white px-3 py-1.5 text-xs font-bold text-[#1C1B1B] opacity-90 shadow-2xs transition-all hover:border-[#B71C1C] hover:bg-[#B71C1C] hover:text-white"
                          >
                            Chamar ({currentCounter.code})
                          </button>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#EFE9E8] pb-4">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#E8F5E9] text-[#1B5E20]">
                  <FlaskConical className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-[#1C1B1B]">
                    Pacientes em Exames / Sala de Espera (Retorno Garantido)
                  </h3>
                  <p className="text-xs text-[#5C5858]">
                    Podem reativar a senha na app Android Totem POS ou ser reintegrados na fila por aqui.
                  </p>
                </div>
              </div>
              <span className="self-start sm:self-center rounded-full bg-[#E8F5E9] px-3 py-1 font-mono-code text-xs font-bold text-[#1B5E20]">
                {examHoldTickets.length} em exame
              </span>
            </div>

            {examHoldTickets.length === 0 ? (
              <div className="py-8 text-center text-xs text-[#787272]">
                Nenhum paciente em espera de exames neste momento.
              </div>
            ) : (
              <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {examHoldTickets.map((ticket) => (
                  <div
                    key={ticket.id}
                    className="flex items-center justify-between rounded-2xl border border-[#DCD4D3] bg-[#FCF9F9] p-3.5"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono-code text-lg font-extrabold text-[#1C1B1B]">
                          {ticket.displayCode}
                        </span>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            QUEUE_CATEGORY_META[ticket.category].badgeBg
                          } ${QUEUE_CATEGORY_META[ticket.category].badgeText}`}
                        >
                          {QUEUE_CATEGORY_META[ticket.category].shortLabel}
                        </span>
                      </div>
                      <p className="mt-0.5 text-[11px] text-[#5C5858]">
                        Atendido antes por: {ticket.assignedCounterName || 'Recepção'}
                      </p>
                    </div>

                    <button
                      onClick={() => reuseTicketByCode(ticket.displayCode)}
                      className="flex items-center gap-1.5 rounded-xl bg-[#1B5E20] px-3 py-2 text-xs font-bold text-white transition-all hover:bg-[#144718]"
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      <span>Voltar à Fila</span>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

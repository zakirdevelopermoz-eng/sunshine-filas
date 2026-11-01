import React, { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Menu,
  X,
  SlidersHorizontal,
  Users,
  Database,
  CheckCircle2,
  Radio,
  Lock,
  Unlock,
  BarChart3,
  Clock,
  FlaskConical,
  MonitorCheck,
  ShieldCheck,
} from 'lucide-react';
import { useQueue } from '../context/QueueContext';

export const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const {
    counters,
    isSupabaseConnected,
    currentTickets,
    currentCycle,
    lockedCounterCode,
    lockWorkstationToCounter,
    unlockWorkstation,
  } = useQueue();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [pinInput, setPinInput] = useState('');
  const [pinError, setPinError] = useState(false);
  const [showPinField, setShowPinField] = useState(false);

  const activeCounters = counters.filter((c) => c.isActive);
  const boundCounter = lockedCounterCode
    ? activeCounters.find((c) => c.code.toUpperCase() === lockedCounterCode.toUpperCase())
    : null;

  // Live Statistics for the Hamburger Drawer
  const waitingTotal = currentTickets.filter((t) => t.status === 'WAITING').length;
  const waitingParticular = currentTickets.filter(
    (t) => t.status === 'WAITING' && t.category === 'PARTICULAR'
  ).length;
  const waitingSeguro = currentTickets.filter(
    (t) => t.status === 'WAITING' && t.category === 'SEGURO'
  ).length;
  const waitingOutros = currentTickets.filter(
    (t) => t.status === 'WAITING' && t.category === 'OUTROS'
  ).length;
  const inExamCount = currentTickets.filter((t) => t.status === 'AWAITING_EXAM').length;
  const completedToday = currentTickets.filter((t) => t.status === 'COMPLETED').length;

  const handleUnlockSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const ok = unlockWorkstation(pinInput);
    if (ok) {
      setShowPinField(false);
      setPinInput('');
      setPinError(false);
    } else {
      setPinError(true);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#F9F8F8] text-[#1C1B1B]">
      {/* Minimalist & Clean Top Navbar */}
      <header className="sticky top-0 z-40 border-b border-[#E5DFDE] bg-white/95 backdrop-blur-xl">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between px-4 py-3 sm:px-6">
          {/* Left: Brand Identity + Subtle Active Workstation Pill */}
          <div className="flex items-center gap-3.5">
            <img
              src="/sunshine-logo.png"
              alt="Sunshine Hospitals"
              className="h-8 sm:h-9 w-auto object-contain"
            />
            <div className="h-5 w-[1px] bg-[#E5DFDE]" />
            <div className="flex items-center gap-2.5">
              <span className="text-xs sm:text-sm font-bold tracking-tight text-[#1C1B1B]">
                {location.pathname.startsWith('/admin')
                  ? 'Administração & CRM'
                  : 'Recepção Central'}
              </span>

              {boundCounter && (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-[#E8F5E9] border border-[#A5D6A7] px-2.5 py-0.5 text-[11px] font-bold text-[#1B5E20]">
                  <Lock className="h-3 w-3" />
                  <span>{boundCounter.name}</span>
                </span>
              )}
            </div>
          </div>

          {/* Right: Minimal Summary Pill + Hamburger Button */}
          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2 rounded-full bg-[#F5F2F2] px-3 py-1 text-xs font-semibold text-[#5C5858]">
              <Clock className="h-3.5 w-3.5 text-[#B71C1C]" />
              <span>{waitingTotal} na fila</span>
            </div>

            <button
              onClick={() => setDrawerOpen(true)}
              aria-label="Abrir Menu e Definições"
              className="flex items-center gap-2 rounded-2xl border border-[#E5DFDE] bg-white px-3.5 py-2 text-xs font-bold text-[#1C1B1B] shadow-2xs transition-all duration-200 hover:border-[#B71C1C] hover:text-[#B71C1C]"
            >
              <Menu className="h-4 w-4" />
              <span className="hidden sm:inline">Menu & Definições</span>
            </button>
          </div>
        </div>
      </header>

      {/* Slide-Over Hamburger Navigation & Settings Drawer */}
      {drawerOpen && (
        <div className="fixed inset-0 z-50 flex justify-end">
          {/* Backdrop */}
          <div
            onClick={() => setDrawerOpen(false)}
            className="fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity"
          />

          {/* Drawer Panel */}
          <aside className="relative z-10 flex h-full w-full max-w-md flex-col justify-between bg-white shadow-2xl border-l border-[#E5DFDE] overflow-y-auto animate-fade-slide-up">
            <div className="p-6 space-y-6">
              {/* Drawer Header */}
              <div className="flex items-center justify-between border-b border-[#EFE9E8] pb-4">
                <div className="flex items-center gap-3">
                  <img
                    src="/sunshine-logo.png"
                    alt="Sunshine Hospitals"
                    className="h-8 w-auto object-contain"
                  />
                  <div>
                    <h2 className="text-sm font-extrabold text-[#1C1B1B]">
                      Painel de Controlo
                    </h2>
                    <p className="text-[11px] text-[#6E6868]">{currentCycle.cycleLabel}</p>
                  </div>
                </div>
                <button
                  onClick={() => setDrawerOpen(false)}
                  className="rounded-full p-2 text-[#5C5858] transition-colors hover:bg-[#F5F2F2] hover:text-[#1C1B1B]"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* 1. Navigation Section */}
              <div className="space-y-2">
                <p className="text-[11px] font-bold uppercase tracking-wider text-[#787272]">
                  Navegação
                </p>
                <div className="grid grid-cols-1 gap-2">
                  <NavLink
                    to="/recepcao"
                    onClick={() => setDrawerOpen(false)}
                    className={({ isActive }) =>
                      `flex items-center justify-between rounded-2xl border px-4 py-3 text-xs font-bold transition-all ${
                        isActive
                          ? 'border-[#B71C1C] bg-[#FFF8F7] text-[#B71C1C]'
                          : 'border-[#EFE9E8] bg-[#FCF9F9] text-[#1C1B1B] hover:border-[#DCD4D3]'
                      }`
                    }
                  >
                    <div className="flex items-center gap-3">
                      <Users className="h-4 w-4" />
                      <span>Terminal de Recepção (Chamadas)</span>
                    </div>
                    <span className="font-mono-code rounded-full bg-[#FFDAD6] px-2 py-0.5 text-[10px] font-bold text-[#93000A]">
                      {waitingTotal}
                    </span>
                  </NavLink>

                  {!lockedCounterCode && (
                    <NavLink
                      to="/admin"
                      onClick={() => setDrawerOpen(false)}
                      className={({ isActive }) =>
                        `flex items-center justify-between rounded-2xl border px-4 py-3 text-xs font-bold transition-all ${
                          isActive
                            ? 'border-[#B71C1C] bg-[#FFF8F7] text-[#B71C1C]'
                            : 'border-[#EFE9E8] bg-[#FCF9F9] text-[#1C1B1B] hover:border-[#DCD4D3]'
                        }`
                      }
                    >
                      <div className="flex items-center gap-3">
                        <SlidersHorizontal className="h-4 w-4" />
                        <span>Admin CRM, Gestão de Balcões & BI</span>
                      </div>
                    </NavLink>
                  )}
                </div>
              </div>

              {/* 2. Live Queue Statistics Section */}
              <div className="space-y-3 border-t border-[#EFE9E8] pt-5">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#787272]">
                    Estatísticas em Tempo Real
                  </span>
                  <BarChart3 className="h-4 w-4 text-[#B71C1C]" />
                </div>

                <div className="grid grid-cols-3 gap-2.5">
                  <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-3 text-center">
                    <span className="text-[10px] font-bold uppercase text-[#93000A]">
                      Particular
                    </span>
                    <div className="mt-1 font-mono-code text-xl font-extrabold text-[#1C1B1B]">
                      {waitingParticular}
                    </div>
                    <span className="text-[10px] text-[#787272]">na fila</span>
                  </div>

                  <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-3 text-center">
                    <span className="text-[10px] font-bold uppercase text-[#1B5E20]">
                      Seguros
                    </span>
                    <div className="mt-1 font-mono-code text-xl font-extrabold text-[#1C1B1B]">
                      {waitingSeguro}
                    </div>
                    <span className="text-[10px] text-[#787272]">na fila</span>
                  </div>

                  <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-3 text-center">
                    <span className="text-[10px] font-bold uppercase text-[#334155]">
                      Outros
                    </span>
                    <div className="mt-1 font-mono-code text-xl font-extrabold text-[#1C1B1B]">
                      {waitingOutros}
                    </div>
                    <span className="text-[10px] text-[#787272]">na fila</span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  <div className="flex items-center justify-between rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] px-3.5 py-2.5">
                    <div className="flex items-center gap-2">
                      <FlaskConical className="h-4 w-4 text-[#1B5E20]" />
                      <span className="text-xs font-semibold text-[#4A4646]">Em Exames</span>
                    </div>
                    <span className="font-mono-code text-sm font-extrabold text-[#1B5E20]">
                      {inExamCount}
                    </span>
                  </div>

                  <div className="flex items-center justify-between rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] px-3.5 py-2.5">
                    <div className="flex items-center gap-2">
                      <CheckCircle2 className="h-4 w-4 text-[#1C1B1B]" />
                      <span className="text-xs font-semibold text-[#4A4646]">Concluídos</span>
                    </div>
                    <span className="font-mono-code text-sm font-extrabold text-[#1C1B1B]">
                      {completedToday}
                    </span>
                  </div>
                </div>
              </div>

              {/* 3. Counter Workstation Assignment / Lock Settings */}
              <div className="space-y-3 border-t border-[#EFE9E8] pt-5">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#787272]">
                    Atribuição de Balcão (Vinculação PWA)
                  </span>
                  <MonitorCheck className="h-4 w-4 text-[#1B5E20]" />
                </div>

                {lockedCounterCode ? (
                  <div className="rounded-2xl border border-[#A5D6A7] bg-[#F1F8E9] p-4 space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="inline-flex items-center gap-1 text-[10px] font-extrabold uppercase tracking-wider text-[#1B5E20]">
                          <Lock className="h-3 w-3" />
                          <span>Terminal Bloqueado</span>
                        </span>
                        <h3 className="mt-0.5 text-sm font-extrabold text-[#1C1B1B]">
                          Este PC está fixado ao Balcão {lockedCounterCode}
                        </h3>
                        <p className="mt-0.5 text-[11px] text-[#4A4646]">
                          O recepcionista só pode operar chamadas do Balcão {lockedCounterCode}.
                        </p>
                      </div>
                    </div>

                    {!showPinField ? (
                      <button
                        onClick={() => setShowPinField(true)}
                        className="w-full flex items-center justify-center gap-2 rounded-xl border border-[#1B5E20]/30 bg-white py-2.5 text-xs font-bold text-[#1B5E20] transition-all hover:bg-[#1B5E20] hover:text-white"
                      >
                        <Unlock className="h-3.5 w-3.5" />
                        <span>Alterar ou Desbloquear Balcão (PIN Admin)</span>
                      </button>
                    ) : (
                      <form onSubmit={handleUnlockSubmit} className="space-y-2 pt-1">
                        <input
                          type="password"
                          value={pinInput}
                          onChange={(e) => {
                            setPinError(false);
                            setPinInput(e.target.value);
                          }}
                          placeholder="PIN Admin (Padrão: 1234)"
                          className="w-full rounded-xl border border-[#DCD4D3] bg-white px-3 py-2 font-mono-code text-xs font-bold text-[#1C1B1B] focus:border-[#B71C1C] focus:outline-none"
                          autoFocus
                        />
                        {pinError && (
                          <p className="text-[11px] font-semibold text-[#B71C1C]">
                            PIN incorreto (use 1234).
                          </p>
                        )}
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => {
                              setShowPinField(false);
                              setPinInput('');
                              setPinError(false);
                            }}
                            className="flex-1 rounded-xl border border-[#DCD4D3] bg-white py-2 text-xs font-bold text-[#5C5858]"
                          >
                            Cancelar
                          </button>
                          <button
                            type="submit"
                            className="flex-1 rounded-xl bg-[#B71C1C] py-2 text-xs font-bold text-white"
                          >
                            Desbloquear
                          </button>
                        </div>
                      </form>
                    )}
                  </div>
                ) : (
                  <div className="rounded-2xl border border-[#EFE9E8] bg-[#FCF9F9] p-4 space-y-3">
                    <p className="text-xs text-[#5C5858]">
                      Selecione abaixo o balcão para fixar este computador (impede que o recepcionista chame senhas de outro balcão):
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      {activeCounters.map((c) => (
                        <button
                          key={c.id}
                          onClick={() => {
                            lockWorkstationToCounter(c.code);
                            setDrawerOpen(false);
                          }}
                          className="flex items-center justify-between rounded-xl border border-[#DCD4D3] bg-white px-3 py-2.5 text-xs font-bold text-[#1C1B1B] transition-all hover:border-[#1B5E20] hover:bg-[#E8F5E9] hover:text-[#1B5E20]"
                        >
                          <span>Fixar {c.name}</span>
                          <Lock className="h-3.5 w-3.5 opacity-60" />
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Drawer Footer: Cloud & API Status */}
            <div className="border-t border-[#EFE9E8] bg-[#FCF9F9] p-5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  {isSupabaseConnected ? (
                    <ShieldCheck className="h-4 w-4 text-[#1B5E20]" />
                  ) : (
                    <Radio className="h-4 w-4 text-[#B71C1C] animate-pulse" />
                  )}
                  <div>
                    <p className="text-xs font-bold text-[#1C1B1B]">
                      {isSupabaseConnected
                        ? 'Supabase PostgreSQL Conectado'
                        : 'Sincronização API Ativa'}
                    </p>
                    <p className="text-[10px] text-[#6E6868]">
                      Totem POS + Smart TV Android em Tempo Real
                    </p>
                  </div>
                </div>
                <Database className="h-4 w-4 text-[#787272]" />
              </div>
            </div>
          </aside>
        </div>
      )}

      {/* Main Content */}
      <main className="flex-1 flex flex-col">{children}</main>
    </div>
  );
};

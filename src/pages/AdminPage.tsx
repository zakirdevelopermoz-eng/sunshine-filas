import React, { useState } from 'react';
import {
  Plus,
  RotateCcw,
  Save,
  SlidersHorizontal,
  Database,
  BarChart3,
  Smartphone,
  CheckCircle2,
  Trash2,
  Copy,
  Check,
  ShieldCheck,
  Building2,
  Layers,
  Clock,
  RefreshCw,
} from 'lucide-react';
import { useQueue } from '../context/QueueContext';
import {
  CounterConfig,
  QUEUE_CATEGORY_META,
  QueueCategory,
} from '../types/queue';
import { getSupabaseCredentials } from '../lib/supabase';

type AdminTab = 'COUNTERS' | 'CYCLES_BI' | 'SUPABASE_KOTLIN';

export const AdminPage: React.FC = () => {
  const {
    counters,
    cycles,
    currentCycle,
    tickets,
    currentTickets,
    isSupabaseConnected,
    updateCounter,
    addCounter,
    deleteCounter,
    resetDailyCounters,
    configureSupabase,
    resetDemoData,
  } = useQueue();

  const [activeTab, setActiveTab] = useState<AdminTab>('COUNTERS');

  // New counter form state
  const [newCode, setNewCode] = useState('E');
  const [newName, setNewName] = useState('Balcão E');
  const [newSubtitle, setNewSubtitle] = useState('Atendimento Misto / Apoio');
  const [newPrimary, setNewPrimary] = useState<QueueCategory[]>(['PARTICULAR']);
  const [newSecondary, setNewSecondary] = useState<QueueCategory[]>(['SEGURO']);

  // Reset cycle label
  const [customCycleLabel, setCustomCycleLabel] = useState('');
  const [resetSuccessBanner, setResetSuccessBanner] = useState(false);

  // Supabase credentials form
  const initialCreds = getSupabaseCredentials();
  const [sbUrl, setSbUrl] = useState(initialCreds.url);
  const [sbKey, setSbKey] = useState(initialCreds.anonKey);
  const [sbStatusMsg, setSbStatusMsg] = useState<string | null>(null);
  const [copiedSnippet, setCopiedSnippet] = useState<string | null>(null);

  const toggleCategoryInList = (
    list: QueueCategory[],
    cat: QueueCategory
  ): QueueCategory[] => {
    return list.includes(cat) ? list.filter((c) => c !== cat) : [...list, cat];
  };

  const handleAddCounter = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCode.trim() || !newName.trim()) return;
    await addCounter({
      code: newCode.trim().toUpperCase(),
      name: newName.trim(),
      subtitle: newSubtitle.trim() || 'Atendimento Recepção',
      serviceUnitId: 'recepcao',
      primaryQueues: newPrimary.length > 0 ? newPrimary : ['PARTICULAR'],
      secondaryQueues: newSecondary,
      supportModeActive: true,
      isActive: true,
    });
    const nextChar = String.fromCharCode(newCode.trim().charCodeAt(0) + 1);
    setNewCode(nextChar);
    setNewName(`Balcão ${nextChar}`);
  };

  const handleResetDaily = async () => {
    await resetDailyCounters(customCycleLabel);
    setCustomCycleLabel('');
    setResetSuccessBanner(true);
    setTimeout(() => setResetSuccessBanner(false), 4500);
  };

  const handleSaveSupabase = async (e: React.FormEvent) => {
    e.preventDefault();
    setSbStatusMsg('A testar ligação ao Supabase PostgreSQL...');
    const ok = await configureSupabase(sbUrl, sbKey);
    if (ok) {
      setSbStatusMsg('Ligado com sucesso ao Supabase PostgreSQL + Realtime!');
    } else if (!sbUrl && !sbKey) {
      setSbStatusMsg('Credenciais removidas. A operar em modo Realtime Local.');
    } else {
      setSbStatusMsg(
        'Credenciais guardadas. Certifique-se de que executou o ficheiro supabase/schema.sql no SQL Editor do Supabase.'
      );
    }
  };

  const copyText = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSnippet(id);
    setTimeout(() => setCopiedSnippet(null), 2000);
  };

  // BI Metrics Calculation across all historical tickets
  const totalHistoricalTickets = tickets.length;
  const totalSeguro = tickets.filter((t) => t.category === 'SEGURO').length;
  const totalParticular = tickets.filter((t) => t.category === 'PARTICULAR').length;
  const totalOutros = tickets.filter((t) => t.category === 'OUTROS').length;
  const totalExamReturns = tickets.filter((t) => t.isReturnFromExam).length;

  return (
    <div className="mx-auto w-full max-w-[1600px] flex-1 px-4 py-6 sm:px-6 space-y-6">
      {/* Header & Sub-Navigation */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
        <div>
          <span className="text-[11px] font-bold uppercase tracking-[0.12em] text-[#B71C1C]">
            Administração Central & Inteligência Hospitalar
          </span>
          <h1 className="text-2xl font-extrabold tracking-tight text-[#1C1B1B]">
            CRM de Gestão de Filas, Balcões & BI
          </h1>
          <p className="text-xs text-[#5C5858]">
            Configure nomes de balcões em tempo real, zere contadores diários sem perder histórico e conecte ao Supabase / Kotlin POS.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setActiveTab('COUNTERS')}
            className={`flex items-center gap-2 rounded-full px-4 py-2.5 text-xs font-bold transition-all ${
              activeTab === 'COUNTERS'
                ? 'bg-[#B71C1C] text-white shadow-xs'
                : 'bg-[#F5F2F2] text-[#4A4646] hover:bg-[#EAE4E3]'
            }`}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            <span>Balcões Variáveis (A, B, C, D+)</span>
          </button>

          <button
            onClick={() => setActiveTab('CYCLES_BI')}
            className={`flex items-center gap-2 rounded-full px-4 py-2.5 text-xs font-bold transition-all ${
              activeTab === 'CYCLES_BI'
                ? 'bg-[#B71C1C] text-white shadow-xs'
                : 'bg-[#F5F2F2] text-[#4A4646] hover:bg-[#EAE4E3]'
            }`}
          >
            <BarChart3 className="h-3.5 w-3.5" />
            <span>Reset Diário & Histórico BI ({totalHistoricalTickets})</span>
          </button>

          <button
            onClick={() => setActiveTab('SUPABASE_KOTLIN')}
            className={`flex items-center gap-2 rounded-full px-4 py-2.5 text-xs font-bold transition-all ${
              activeTab === 'SUPABASE_KOTLIN'
                ? 'bg-[#1B5E20] text-white shadow-xs'
                : 'bg-[#E8F5E9] text-[#1B5E20] hover:bg-[#C8E6C9]'
            }`}
          >
            <Database className="h-3.5 w-3.5" />
            <span>Supabase Cloud & App Kotlin POS</span>
          </button>
        </div>
      </div>

      {/* TAB 1: DYNAMIC COUNTERS MANAGEMENT */}
      {activeTab === 'COUNTERS' && (
        <div className="animate-fade-slide-up grid grid-cols-1 xl:grid-cols-12 gap-6">
          {/* Existing Counters List (Editable in real time) */}
          <div className="xl:col-span-8 space-y-4">
            <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
              <div className="flex items-center justify-between border-b border-[#EFE9E8] pb-4">
                <div>
                  <h2 className="text-lg font-extrabold text-[#1C1B1B]">
                    Balcões de Atendimento Configurados ({counters.length})
                  </h2>
                  <p className="text-xs text-[#5C5858]">
                    Altere o código (A, B, C, D), o nome de exibição ou as filas primárias e de apoio a qualquer momento.
                  </p>
                </div>
                <button
                  onClick={resetDemoData}
                  className="flex items-center gap-1.5 rounded-full border border-[#DCD4D3] px-3.5 py-1.5 text-xs font-semibold text-[#5C5858] transition-all hover:border-[#B71C1C] hover:text-[#B71C1C]"
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  <span>Restaurar Dados Padrão</span>
                </button>
              </div>

              <div className="mt-5 space-y-4">
                {counters.map((counter) => (
                  <CounterEditorRow
                    key={counter.id}
                    counter={counter}
                    onSave={updateCounter}
                    onDelete={deleteCounter}
                    canDelete={counters.length > 1}
                  />
                ))}
              </div>
            </div>

            {/* Future Service Units Roadmap Card */}
            <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
              <div className="flex items-center gap-3 border-b border-[#EFE9E8] pb-4">
                <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#F2ECEB] text-[#1C1B1B]">
                  <Building2 className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-[#1C1B1B]">
                    Arquitetura Preparada para Outras Unidades (Expansão Futura)
                  </h3>
                  <p className="text-xs text-[#5C5858]">
                    Atualmente ativo apenas para Recepção Central. O modelo de dados já suporta ativação futura de novas unidades caso o hospital acorde.
                  </p>
                </div>
              </div>

              <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="rounded-2xl border-2 border-[#1B5E20] bg-[#F1F8E9] p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-extrabold uppercase text-[#1B5E20]">
                      Módulo Ativo
                    </span>
                    <CheckCircle2 className="h-4 w-4 text-[#1B5E20]" />
                  </div>
                  <h4 className="mt-2 text-base font-bold text-[#1C1B1B]">
                    Recepção Central
                  </h4>
                  <p className="mt-1 text-xs text-[#4A4646]">
                    Faturação Particular, Seguros, Agendamentos, Cotações e Retorno de Exames.
                  </p>
                </div>

                <div className="rounded-2xl border border-dashed border-[#DCD4D3] bg-[#FCF9F9] p-4 opacity-80">
                  <span className="rounded-full bg-[#EAE4E3] px-2.5 py-0.5 text-[10px] font-bold uppercase text-[#5C5858]">
                    Expansão Fase 2 (Pronto na BD)
                  </span>
                  <h4 className="mt-2 text-base font-bold text-[#1C1B1B]">
                    Gabinetes Médicos & Triagem
                  </h4>
                  <p className="mt-1 text-xs text-[#5C5858]">
                    Encaminhamento da mesma senha da Recepção diretamente para o ecrã do médico no consultório.
                  </p>
                </div>

                <div className="rounded-2xl border border-dashed border-[#DCD4D3] bg-[#FCF9F9] p-4 opacity-80">
                  <span className="rounded-full bg-[#EAE4E3] px-2.5 py-0.5 text-[10px] font-bold uppercase text-[#5C5858]">
                    Expansão Fase 3 (Pronto na BD)
                  </span>
                  <h4 className="mt-2 text-base font-bold text-[#1C1B1B]">
                    Radiologia, S.O. & Laboratório
                  </h4>
                  <p className="mt-1 text-xs text-[#5C5858]">
                    Chamada interna nas salas de exame com devolução automática da senha para a Recepção após conclusão.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Add New Counter Form */}
          <div className="xl:col-span-4">
            <form
              onSubmit={handleAddCounter}
              className="sticky top-20 rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs space-y-5"
            >
              <div className="border-b border-[#EFE9E8] pb-4">
                <span className="text-[11px] font-bold uppercase tracking-wider text-[#B71C1C]">
                  Escalabilidade Instantânea
                </span>
                <h3 className="text-lg font-extrabold text-[#1C1B1B]">
                  Adicionar Novo Balcão
                </h3>
                <p className="text-xs text-[#5C5858]">
                  Crie um novo balcão (ex: Balcão E, Caixa Prioritário, etc.) e defina que filas atende.
                </p>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-bold text-[#4A4646] mb-1">
                    Sigla / Letra
                  </label>
                  <input
                    type="text"
                    maxLength={4}
                    value={newCode}
                    onChange={(e) => setNewCode(e.target.value)}
                    className="w-full rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-3 py-2.5 font-mono-code text-sm font-bold text-[#1C1B1B] focus:border-[#B71C1C] focus:bg-white focus:outline-none"
                    placeholder="E"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-xs font-bold text-[#4A4646] mb-1">
                    Nome do Balcão
                  </label>
                  <input
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    className="w-full rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-3.5 py-2.5 text-sm font-semibold text-[#1C1B1B] focus:border-[#B71C1C] focus:bg-white focus:outline-none"
                    placeholder="Ex: Balcão E"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-[#4A4646] mb-1">
                  Descrição / Especialidade
                </label>
                <input
                  type="text"
                  value={newSubtitle}
                  onChange={(e) => setNewSubtitle(e.target.value)}
                  className="w-full rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-3.5 py-2.5 text-xs font-medium text-[#1C1B1B] focus:border-[#B71C1C] focus:bg-white focus:outline-none"
                  placeholder="Ex: Atendimento Particular & Seguros"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-[#4A4646] mb-1.5">
                  Filas Primárias (Prioridade Direta)
                </label>
                <div className="flex flex-wrap gap-2">
                  {(['PARTICULAR', 'SEGURO', 'OUTROS'] as QueueCategory[]).map((cat) => {
                    const active = newPrimary.includes(cat);
                    return (
                      <button
                        type="button"
                        key={cat}
                        onClick={() =>
                          setNewPrimary(toggleCategoryInList(newPrimary, cat))
                        }
                        className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition-all ${
                          active
                            ? 'border-[#B71C1C] bg-[#B71C1C] text-white'
                            : 'border-[#DCD4D3] bg-[#FCF9F9] text-[#5C5858]'
                        }`}
                      >
                        {QUEUE_CATEGORY_META[cat].shortLabel}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-[#4A4646] mb-1.5">
                  Filas Secundárias (Apoio / Transbordo)
                </label>
                <div className="flex flex-wrap gap-2">
                  {(['PARTICULAR', 'SEGURO', 'OUTROS'] as QueueCategory[]).map((cat) => {
                    const active = newSecondary.includes(cat);
                    return (
                      <button
                        type="button"
                        key={cat}
                        onClick={() =>
                          setNewSecondary(toggleCategoryInList(newSecondary, cat))
                        }
                        className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition-all ${
                          active
                            ? 'border-[#1B5E20] bg-[#1B5E20] text-white'
                            : 'border-[#DCD4D3] bg-[#FCF9F9] text-[#5C5858]'
                        }`}
                      >
                        {QUEUE_CATEGORY_META[cat].shortLabel}
                      </button>
                    );
                  })}
                </div>
              </div>

              <button
                type="submit"
                className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#1C1B1B] py-3.5 text-xs font-bold text-white transition-all hover:bg-[#B71C1C]"
              >
                <Plus className="h-4 w-4" />
                <span>Adicionar Balcão ao Sistema</span>
              </button>
            </form>
          </div>
        </div>
      )}

      {/* TAB 2: DAILY COUNTER RESET & HISTORICAL BI (MONETIZATION / ANALYTICS) */}
      {activeTab === 'CYCLES_BI' && (
        <div className="animate-fade-slide-up space-y-6">
          {/* Non-Destructive Daily Counter Reset Card */}
          <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
              <div className="lg:col-span-7 space-y-2">
                <div className="inline-flex items-center gap-2 rounded-full bg-[#FFDAD6]/60 px-3 py-1 text-xs font-bold text-[#93000A]">
                  <RotateCcw className="h-3.5 w-3.5" />
                  <span>Gestão de Turno / Reset Diário Inteligente</span>
                </div>
                <h2 className="text-xl font-extrabold text-[#1C1B1B]">
                  Zerar Contadores de Senhas (Reiniciar em P-001, S-001, G-001)
                </h2>
                <p className="text-xs leading-relaxed text-[#5C5858]">
                  Ao zerar os contadores, o sistema encerra o turno atual (
                  <strong className="text-[#1C1B1B]">{currentCycle.cycleLabel}</strong>) e inicia uma nova sequência a partir do{' '}
                  <strong className="font-mono-code text-[#1C1B1B]">001</strong>. Todas as{' '}
                  <strong className="text-[#1B5E20]">{totalHistoricalTickets} senhas anteriores permanecem guardadas na Base de Dados</strong>{' '}
                  para relatórios de produtividade, SLA e auditoria hospitalar.
                </p>
              </div>

              <div className="lg:col-span-5 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                <input
                  type="text"
                  value={customCycleLabel}
                  onChange={(e) => setCustomCycleLabel(e.target.value)}
                  placeholder="Nome opcional (ex: Turno da Manhã)"
                  className="flex-1 rounded-2xl border border-[#DCD4D3] bg-[#FCF9F9] px-4 py-3 text-xs font-medium text-[#1C1B1B] focus:border-[#B71C1C] focus:bg-white focus:outline-none"
                />
                <button
                  onClick={handleResetDaily}
                  className="flex items-center justify-center gap-2 rounded-2xl bg-[#B71C1C] px-6 py-3.5 text-xs font-bold text-white shadow-sm transition-all hover:bg-[#93000A]"
                >
                  <RotateCcw className="h-4 w-4" />
                  <span>Zerar Contadores Agora</span>
                </button>
              </div>
            </div>

            {resetSuccessBanner && (
              <div className="mt-4 flex items-center gap-2 rounded-2xl bg-[#E8F5E9] border border-[#A5D6A7] px-4 py-3 text-xs font-bold text-[#1B5E20]">
                <CheckCircle2 className="h-4 w-4" />
                <span>
                  Contadores zerados com sucesso! As próximas senhas iniciarão em P-001, S-001 e G-001, e o turno anterior foi arquivado no histórico BI.
                </span>
              </div>
            )}
          </div>

          {/* BI Analytics Cards (Demonstrating SaaS Upsell Value) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="rounded-[24px] border border-[#E5DFDE] bg-white p-5">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6E6868]">
                Total Acumulado na BD
              </span>
              <div className="mt-2 font-mono-code text-3xl font-extrabold text-[#1C1B1B]">
                {totalHistoricalTickets}
              </div>
              <p className="mt-1 text-xs text-[#1B5E20] font-semibold">
                {currentTickets.length} no turno atual · {cycles.length} turnos registados
              </p>
            </div>

            <div className="rounded-[24px] border border-[#E5DFDE] bg-white p-5">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6E6868]">
                Pacientes com Seguro (Série S)
              </span>
              <div className="mt-2 font-mono-code text-3xl font-extrabold text-[#1B5E20]">
                {totalSeguro}
              </div>
              <p className="mt-1 text-xs text-[#5C5858]">
                {totalHistoricalTickets > 0
                  ? `${Math.round((totalSeguro / totalHistoricalTickets) * 100)}% do volume total hospitalar`
                  : '0%'}
              </p>
            </div>

            <div className="rounded-[24px] border border-[#E5DFDE] bg-white p-5">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6E6868]">
                Pacientes Particulares (Série P)
              </span>
              <div className="mt-2 font-mono-code text-3xl font-extrabold text-[#B71C1C]">
                {totalParticular}
              </div>
              <p className="mt-1 text-xs text-[#5C5858]">
                Mais {totalOutros} senhas de Cotações / Agendamentos
              </p>
            </div>

            <div className="rounded-[24px] border border-[#E5DFDE] bg-white p-5">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6E6868]">
                Senhas Reutilizadas (Pós-Exame)
              </span>
              <div className="mt-2 font-mono-code text-3xl font-extrabold text-[#1C1B1B]">
                {totalExamReturns}
              </div>
              <p className="mt-1 text-xs text-[#5C5858]">
                Pacientes que voltaram de exames sem perder a vez
              </p>
            </div>
          </div>

          {/* Complete Historical Audit Table across Cycles */}
          <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#EFE9E8] pb-4">
              <div>
                <h3 className="text-base font-extrabold text-[#1C1B1B]">
                  Registo Histórico Perpétuo de Senhas (Todos os Turnos / Resets)
                </h3>
                <p className="text-xs text-[#5C5858]">
                  Prova de como os resets diários reiniciam a numeração visual mantendo 100% dos dados para auditoria e relatórios executivos.
                </p>
              </div>
              <span className="font-mono-code rounded-full bg-[#F2ECEB] px-3 py-1 text-xs font-bold text-[#1C1B1B]">
                {tickets.length} registos preservados
              </span>
            </div>

            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[#EFE9E8] text-[11px] font-bold uppercase tracking-wider text-[#6E6868]">
                    <th className="py-3 px-3">Senha</th>
                    <th className="py-3 px-3">Turno / Ciclo</th>
                    <th className="py-3 px-3">Categoria & Serviço</th>
                    <th className="py-3 px-3">Balcão</th>
                    <th className="py-3 px-3">Retorno Exame</th>
                    <th className="py-3 px-3">Estado</th>
                    <th className="py-3 px-3">Hora Emissão</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F5F2F2] text-xs">
                  {[...tickets].reverse().map((t) => {
                    const cycleObj = cycles.find((c) => c.id === t.cycleId);
                    return (
                      <tr key={t.id} className="hover:bg-[#FCF9F9]">
                        <td className="py-3 px-3 font-mono-code font-bold text-sm text-[#1C1B1B]">
                          {t.displayCode}
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                              cycleObj?.isCurrent
                                ? 'bg-[#E8F5E9] text-[#1B5E20]'
                                : 'bg-[#F2ECEB] text-[#5C5858]'
                            }`}
                          >
                            {cycleObj?.cycleLabel || 'Turno Anterior'}
                          </span>
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className={`mr-2 rounded-md px-2 py-0.5 text-[10px] font-bold ${
                              QUEUE_CATEGORY_META[t.category].badgeBg
                            } ${QUEUE_CATEGORY_META[t.category].badgeText}`}
                          >
                            {QUEUE_CATEGORY_META[t.category].shortLabel}
                          </span>
                          <span className="text-[#4A4646]">{t.subServiceLabel}</span>
                        </td>
                        <td className="py-3 px-3 font-semibold text-[#1C1B1B]">
                          {t.assignedCounterName || '—'}
                        </td>
                        <td className="py-3 px-3">
                          {t.isReturnFromExam ? (
                            <span className="rounded-md bg-[#1B5E20] px-2 py-0.5 text-[10px] font-bold text-white">
                              Sim ({t.reuseCount}x)
                            </span>
                          ) : (
                            <span className="text-[#8C8585]">Não</span>
                          )}
                        </td>
                        <td className="py-3 px-3 font-bold text-[#1C1B1B]">
                          {t.status}
                        </td>
                        <td className="py-3 px-3 font-mono-code text-[#6E6868]">
                          {new Date(t.createdAt).toLocaleTimeString('pt-PT', {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: SUPABASE CONNECTION & KOTLIN POS ANDROID INTEGRATION */}
      {activeTab === 'SUPABASE_KOTLIN' && (
        <div className="animate-fade-slide-up grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left 6 cols: Live Supabase Credentials Configuration */}
          <div className="lg:col-span-6 space-y-6">
            <form
              onSubmit={handleSaveSupabase}
              className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs space-y-5"
            >
              <div className="flex items-center justify-between border-b border-[#EFE9E8] pb-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#E8F5E9] text-[#1B5E20]">
                    <Database className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-extrabold text-[#1C1B1B]">
                      Ligar ao Supabase PostgreSQL + Realtime
                    </h2>
                    <p className="text-xs text-[#5C5858]">
                      Cole a URL e a Anon Key do seu projeto Supabase para sincronizar na cloud.
                    </p>
                  </div>
                </div>
                <span
                  className={`rounded-full px-3 py-1 text-xs font-bold ${
                    isSupabaseConnected
                      ? 'bg-[#C8E6C9] text-[#1B5E20]'
                      : 'bg-[#F2ECEB] text-[#5C5858]'
                  }`}
                >
                  {isSupabaseConnected ? 'Conectado' : 'Modo Local'}
                </span>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-[#1C1B1B] mb-1">
                    Supabase Project URL (VITE_SUPABASE_URL)
                  </label>
                  <input
                    type="url"
                    value={sbUrl}
                    onChange={(e) => setSbUrl(e.target.value)}
                    placeholder="https://xyzcompany.supabase.co"
                    className="w-full rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-4 py-2.5 font-mono-code text-xs text-[#1C1B1B] focus:border-[#1B5E20] focus:bg-white focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-[#1C1B1B] mb-1">
                    Supabase Anon Public Key (VITE_SUPABASE_ANON_KEY)
                  </label>
                  <input
                    type="password"
                    value={sbKey}
                    onChange={(e) => setSbKey(e.target.value)}
                    placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                    className="w-full rounded-xl border border-[#DCD4D3] bg-[#FCF9F9] px-4 py-2.5 font-mono-code text-xs text-[#1C1B1B] focus:border-[#1B5E20] focus:bg-white focus:outline-none"
                  />
                </div>
              </div>

              {sbStatusMsg && (
                <div className="rounded-xl bg-[#F1F8E9] border border-[#A5D6A7] p-3 text-xs font-semibold text-[#1B5E20]">
                  {sbStatusMsg}
                </div>
              )}

              <div className="flex items-center justify-between gap-3 pt-2">
                <span className="text-[11px] text-[#6E6868]">
                  O script SQL completo está em <code className="font-mono-code text-[#1C1B1B]">supabase/schema.sql</code>
                </span>
                <button
                  type="submit"
                  className="flex items-center gap-2 rounded-2xl bg-[#1B5E20] px-5 py-3 text-xs font-bold text-white transition-all hover:bg-[#144718]"
                >
                  <Save className="h-4 w-4" />
                  <span>Guardar e Conectar</span>
                </button>
              </div>
            </form>

            {/* Vercel Serverless API Endpoints Summary */}
            <div className="rounded-[28px] border border-[#E5DFDE] bg-white p-6 shadow-2xs space-y-4">
              <div className="flex items-center gap-3 border-b border-[#EFE9E8] pb-3">
                <Layers className="h-5 w-5 text-[#B71C1C]" />
                <div>
                  <h3 className="text-base font-extrabold text-[#1C1B1B]">
                    Endpoints REST & Realtime para Totem POS e Smart TV
                  </h3>
                  <p className="text-xs text-[#5C5858]">
                    A app Kotlin no tablet POS comunica diretamente com a API REST / Supabase:
                  </p>
                </div>
              </div>

              <div className="space-y-2.5 font-mono-code text-xs">
                <div className="rounded-xl bg-[#FCF9F9] border border-[#EFE9E8] p-3">
                  <span className="font-bold text-[#1B5E20]">POST</span>{' '}
                  <span className="text-[#1C1B1B]">/rest/v1/tickets</span>
                  <p className="font-sans text-[11px] text-[#5C5858] mt-1">
                    Cria uma nova senha (Particular, Seguro ou Outros) no ciclo ativo e aciona o WebSocket para a Smart TV e Recepção.
                  </p>
                </div>
                <div className="rounded-xl bg-[#FCF9F9] border border-[#EFE9E8] p-3">
                  <span className="font-bold text-[#B71C1C]">PATCH</span>{' '}
                  <span className="text-[#1C1B1B]">
                    /rest/v1/tickets?display_code=eq.S-001
                  </span>
                  <p className="font-sans text-[11px] text-[#5C5858] mt-1">
                    Reutiliza a senha após exames (<code className="font-mono-code">is_return_from_exam = true, status = WAITING</code>) reposicionando o paciente com prioridade.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right 6 cols: Kotlin Jetpack Compose + ESC/POS Reference Code */}
          <div className="lg:col-span-6">
            <div className="rounded-[28px] border border-[#E5DFDE] bg-[#1C1B1B] p-6 text-white shadow-2xs space-y-4">
              <div className="flex items-center justify-between border-b border-white/10 pb-4">
                <div className="flex items-center gap-3">
                  <Smartphone className="h-5 w-5 text-[#A5D6A7]" />
                  <div>
                    <h3 className="text-base font-bold">
                      Módulo Android Kotlin (Totem POS + Impressora Térmica)
                    </h3>
                    <p className="text-xs text-white/60">
                      Código incluído em <code className="font-mono-code text-[#A5D6A7]">android-kiosk-kotlin/SunshinePosKiosk.kt</code>
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => copyText('kotlin', KOTLIN_SAMPLE_SNIPPET)}
                  className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs font-semibold text-white hover:bg-white/20"
                >
                  {copiedSnippet === 'kotlin' ? (
                    <>
                      <Check className="h-3.5 w-3.5 text-[#A5D6A7]" />
                      <span>Copiado</span>
                    </>
                  ) : (
                    <>
                      <Copy className="h-3.5 w-3.5" />
                      <span>Copiar Kotlin</span>
                    </>
                  )}
                </button>
              </div>

              <pre className="font-mono-code text-[11px] leading-relaxed text-[#E2E8F0] overflow-x-auto max-h-[430px]">
                <code>{KOTLIN_SAMPLE_SNIPPET}</code>
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const CounterEditorRow: React.FC<{
  counter: CounterConfig;
  onSave: (c: CounterConfig) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
  canDelete: boolean;
}> = ({ counter, onSave, onDelete, canDelete }) => {
  const [code, setCode] = useState(counter.code);
  const [name, setName] = useState(counter.name);
  const [subtitle, setSubtitle] = useState(counter.subtitle);
  const [primary, setPrimary] = useState<QueueCategory[]>(counter.primaryQueues);
  const [secondary, setSecondary] = useState<QueueCategory[]>(counter.secondaryQueues);
  const [isActive, setIsActive] = useState(counter.isActive);
  const [savedToast, setSavedToast] = useState(false);

  const toggleCat = (list: QueueCategory[], cat: QueueCategory) =>
    list.includes(cat) ? list.filter((x) => x !== cat) : [...list, cat];

  const handleSave = async () => {
    await onSave({
      ...counter,
      code: code.trim().toUpperCase() || counter.code,
      name: name.trim() || counter.name,
      subtitle: subtitle.trim(),
      primaryQueues: primary.length > 0 ? primary : ['PARTICULAR'],
      secondaryQueues: secondary,
      isActive,
    });
    setSavedToast(true);
    setTimeout(() => setSavedToast(false), 2000);
  };

  return (
    <div className="rounded-2xl border border-[#E5DFDE] bg-[#FCF9F9] p-4 transition-all hover:border-[#B8AEAD]">
      <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-center">
        <div className="md:col-span-2 flex items-center gap-2">
          <input
            type="text"
            maxLength={4}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="w-14 rounded-xl border border-[#DCD4D3] bg-white px-2.5 py-2 text-center font-mono-code text-sm font-extrabold text-[#B71C1C] focus:border-[#B71C1C] focus:outline-none"
          />
          <label className="flex items-center gap-1.5 text-xs font-semibold text-[#4A4646] cursor-pointer">
            <input
              type="checkbox"
              checked={isActive}
              onChange={(e) => setIsActive(e.target.checked)}
              className="accent-[#1B5E20] h-4 w-4 rounded"
            />
            <span>Ativo</span>
          </label>
        </div>

        <div className="md:col-span-3">
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-xl border border-[#DCD4D3] bg-white px-3 py-2 text-sm font-bold text-[#1C1B1B] focus:border-[#B71C1C] focus:outline-none"
            placeholder="Nome do Balcão"
          />
        </div>

        <div className="md:col-span-4">
          <input
            type="text"
            value={subtitle}
            onChange={(e) => setSubtitle(e.target.value)}
            className="w-full rounded-xl border border-[#DCD4D3] bg-white px-3 py-2 text-xs text-[#4A4646] focus:border-[#B71C1C] focus:outline-none"
            placeholder="Descrição do Balcão"
          />
        </div>

        <div className="md:col-span-3 flex items-center justify-end gap-2">
          <button
            onClick={handleSave}
            className="flex items-center gap-1.5 rounded-xl bg-[#1C1B1B] px-3.5 py-2 text-xs font-bold text-white transition-all hover:bg-[#1B5E20]"
          >
            {savedToast ? (
              <>
                <Check className="h-3.5 w-3.5 text-[#A5D6A7]" />
                <span>Atualizado</span>
              </>
            ) : (
              <>
                <Save className="h-3.5 w-3.5" />
                <span>Guardar</span>
              </>
            )}
          </button>

          {canDelete && (
            <button
              onClick={() => onDelete(counter.id)}
              title="Remover Balcão"
              className="rounded-xl border border-[#E5DFDE] bg-white p-2 text-[#6E6868] transition-all hover:border-[#B71C1C] hover:text-[#B71C1C]"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      {/* Queue Assignments */}
      <div className="mt-3 flex flex-wrap items-center justify-between gap-4 border-t border-[#EFE9E8] pt-3 text-xs">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-bold text-[#5C5858]">Fila Principal:</span>
          {(['PARTICULAR', 'SEGURO', 'OUTROS'] as QueueCategory[]).map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setPrimary(toggleCat(primary, cat))}
              className={`rounded-lg px-2.5 py-1 text-[11px] font-bold transition-all ${
                primary.includes(cat)
                  ? 'bg-[#B71C1C] text-white'
                  : 'bg-white border border-[#DCD4D3] text-[#6E6868]'
              }`}
            >
              {QUEUE_CATEGORY_META[cat].shortLabel}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="font-bold text-[#5C5858]">Apoio / Secundária:</span>
          {(['PARTICULAR', 'SEGURO', 'OUTROS'] as QueueCategory[]).map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setSecondary(toggleCat(secondary, cat))}
              className={`rounded-lg px-2.5 py-1 text-[11px] font-bold transition-all ${
                secondary.includes(cat)
                  ? 'bg-[#1B5E20] text-white'
                  : 'bg-white border border-[#DCD4D3] text-[#6E6868]'
              }`}
            >
              {QUEUE_CATEGORY_META[cat].shortLabel}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

const KOTLIN_SAMPLE_SNIPPET = `// SunshinePosKiosk.kt - Jetpack Compose Material 3 + ESC/POS Thermal Printer
package com.sunshinehospitals.poskiosk

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime

val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Postgrest)
    install(Realtime)
}

// 1. Emitir Senha (Particular, Seguro ou Outros Serviços) e Imprimir via POS
suspend fun issueAndPrintTicket(
    category: String, // "PARTICULAR" | "SEGURO" | "OUTROS"
    subService: String,
    subServiceLabel: String,
    printer: EscPosThermalPrinter
): TicketDto {
    val ticket = supabase.from("tickets").insert(
        mapOf(
            "category" to category,
            "sub_service" to subService,
            "sub_service_label" to subServiceLabel,
            "status" to "WAITING"
        )
    ) { select() }.decodeSingle<TicketDto>()

    printer.printSunshineTicket(
        displayCode = ticket.displayCode,
        categoryLabel = subServiceLabel,
        isReturn = false
    )
    return ticket
}

// 2. Reutilizar Senha após Exames (sem ir para o fim da fila)
suspend fun reuseTicketAfterExam(displayCode: String): TicketDto {
    return supabase.from("tickets").update(
        mapOf(
            "status" to "WAITING",
            "is_return_from_exam" to true,
            "queued_at" to Instant.now().toString()
        )
    ) {
        filter { eq("display_code", displayCode.uppercase()) }
        select()
    }.decodeSingle<TicketDto>()
}`;

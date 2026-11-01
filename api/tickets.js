import { createClient } from '@supabase/supabase-js';

function getSupabase() {
  const url = process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL;
  const key = process.env.VITE_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY;
  if (!url || !key) return null;
  return createClient(url, key);
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method Not Allowed' });
  }

  const supabase = getSupabase();
  if (!supabase) {
    return res.status(503).json({
      error: 'Configure VITE_SUPABASE_URL e VITE_SUPABASE_ANON_KEY nas Environment Variables da Vercel.',
    });
  }

  const { category = 'PARTICULAR', subService = 'CONSULTA_PARTICULAR', subServiceLabel = 'Atendimento' } =
    req.body || {};

  // Get active cycle
  const { data: cycles } = await supabase
    .from('queue_cycles')
    .select('*')
    .eq('is_current', true)
    .limit(1);

  const currentCycleId = cycles?.[0]?.id;
  if (!currentCycleId) {
    return res.status(500).json({ error: 'Nenhum ciclo ativo encontrado no Supabase.' });
  }

  // Get max sequence in active cycle for category
  const { data: existing } = await supabase
    .from('tickets')
    .select('sequence_number')
    .eq('cycle_id', currentCycleId)
    .eq('category', category)
    .order('sequence_number', { ascending: false })
    .limit(1);

  const nextSeq = (existing?.[0]?.sequence_number || 0) + 1;
  const prefixMap = { PARTICULAR: 'P', SEGURO: 'S', OUTROS: 'G' };
  const prefix = prefixMap[category] || 'P';
  const displayCode = `${prefix}-${String(nextSeq).padStart(3, '0')}`;
  const nowIso = new Date().toISOString();

  const { data: inserted, error } = await supabase
    .from('tickets')
    .insert({
      cycle_id: currentCycleId,
      service_unit_id: 'recepcao',
      category,
      sub_service: subService,
      sub_service_label: subServiceLabel,
      sequence_number: nextSeq,
      display_code: displayCode,
      status: 'WAITING',
      is_return_from_exam: false,
      reuse_count: 0,
      call_count: 0,
      created_at: nowIso,
      queued_at: nowIso,
    })
    .select('*')
    .single();

  if (error || !inserted) {
    return res.status(500).json({ error: error?.message || 'Erro ao criar senha' });
  }

  return res.status(201).json({
    id: inserted.id,
    cycleId: inserted.cycle_id,
    serviceUnitId: inserted.service_unit_id,
    category: inserted.category,
    subService: inserted.sub_service,
    subServiceLabel: inserted.sub_service_label,
    sequenceNumber: inserted.sequence_number,
    displayCode: inserted.display_code,
    status: inserted.status,
    isReturnFromExam: inserted.is_return_from_exam,
    reuseCount: inserted.reuse_count,
    callCount: inserted.call_count,
    createdAt: inserted.created_at,
    queuedAt: inserted.queued_at,
  });
}

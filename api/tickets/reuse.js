import { createClient } from '@supabase/supabase-js';

const DEFAULT_URL = 'https://xpysyyfogouxapchvkwb.supabase.co';
const DEFAULT_KEY = 'sb_publishable_RJFeXPv9PuWg3YMgcajwaQ_bkDmfQ63';

function getSupabase() {
  const url = process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL || DEFAULT_URL;
  const key =
    process.env.VITE_SUPABASE_PUBLISHABLE_KEY ||
    process.env.VITE_SUPABASE_ANON_KEY ||
    process.env.SUPABASE_ANON_KEY ||
    DEFAULT_KEY;
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

  const rawCode = String(req.body?.displayCode || '')
    .trim()
    .toUpperCase()
    .replace(/\s+/g, '');
  const formatted = rawCode.includes('-')
    ? rawCode
    : rawCode.replace(/^([A-Z]+)(\d+)$/, (_, p, n) => `${p}-${n.padStart(3, '0')}`);

  const { data: found } = await supabase
    .from('tickets')
    .select('*')
    .ilike('display_code', formatted)
    .order('created_at', { ascending: false })
    .limit(1);

  const target = found?.[0];
  if (!target) {
    return res.status(404).json({
      ok: false,
      message: `Senha "${formatted}" não encontrada no turno atual.`,
    });
  }

  if (target.status === 'WAITING') {
    return res.status(400).json({
      ok: false,
      message: `A senha ${target.display_code} já se encontra na fila de espera.`,
    });
  }

  const nowIso = new Date().toISOString();
  const { data: updated, error } = await supabase
    .from('tickets')
    .update({
      status: 'WAITING',
      is_return_from_exam: true,
      reuse_count: (target.reuse_count || 0) + 1,
      returned_from_exam_at: nowIso,
      queued_at: nowIso,
    })
    .eq('id', target.id)
    .select('*')
    .single();

  if (error || !updated) {
    return res.status(500).json({
      ok: false,
      message: error?.message || 'Erro ao reativar senha.',
    });
  }

  return res.status(200).json({
    ok: true,
    message: `Senha ${updated.display_code} reativada com prioridade de Retorno de Exame.`,
    ticket: {
      id: updated.id,
      displayCode: updated.display_code,
      category: updated.category,
      subService: updated.sub_service,
      subServiceLabel: updated.sub_service_label,
      status: updated.status,
      isReturnFromExam: updated.is_return_from_exam,
      reuseCount: updated.reuse_count,
      queuedAt: updated.queued_at,
    },
  });
}

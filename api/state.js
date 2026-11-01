import { createClient } from '@supabase/supabase-js';

function getSupabase() {
  const url = process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL;
  const key = process.env.VITE_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY;
  if (!url || !key) return null;
  return createClient(url, key);
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const supabase = getSupabase();
  if (!supabase) {
    return res.status(503).json({
      error: 'Configure VITE_SUPABASE_URL e VITE_SUPABASE_ANON_KEY nas Environment Variables da Vercel.',
    });
  }

  const [countersRes, cyclesRes, ticketsRes] = await Promise.all([
    supabase.from('counters').select('*').order('display_order', { ascending: true }),
    supabase.from('queue_cycles').select('*').order('started_at', { ascending: true }),
    supabase.from('tickets').select('*').order('created_at', { ascending: true }),
  ]);

  const counters = (countersRes.data || []).map((row) => ({
    id: row.id,
    code: row.code,
    name: row.name,
    subtitle: row.subtitle,
    serviceUnitId: row.service_unit_id,
    primaryQueues: row.primary_queues,
    secondaryQueues: row.secondary_queues,
    supportModeActive: row.support_mode_active,
    isActive: row.is_active,
    displayOrder: row.display_order,
  }));

  const cycles = (cyclesRes.data || []).map((row) => ({
    id: row.id,
    serviceUnitId: row.service_unit_id,
    cycleLabel: row.cycle_label,
    isCurrent: row.is_current,
    startedAt: row.started_at,
    closedAt: row.closed_at,
    resetBy: row.reset_by,
  }));

  const tickets = (ticketsRes.data || []).map((row) => ({
    id: row.id,
    cycleId: row.cycle_id,
    serviceUnitId: row.service_unit_id,
    category: row.category,
    subService: row.sub_service,
    subServiceLabel: row.sub_service_label,
    sequenceNumber: row.sequence_number,
    displayCode: row.display_code,
    status: row.status,
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

  return res.status(200).json({
    counters,
    cycles,
    tickets,
    updatedAt: Date.now(),
  });
}

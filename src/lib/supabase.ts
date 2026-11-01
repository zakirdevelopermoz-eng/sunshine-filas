import { createClient, SupabaseClient } from '@supabase/supabase-js';

const STORAGE_URL_KEY = 'sunshine_supabase_url';
const STORAGE_ANON_KEY = 'sunshine_supabase_anon_key';

const DEFAULT_SUPABASE_URL = 'https://xpysyyfogouxapchvkwb.supabase.co';
const DEFAULT_SUPABASE_KEY = 'sb_publishable_RJFeXPv9PuWg3YMgcajwaQ_bkDmfQ63';

export interface SupabaseCredentials {
  url: string;
  anonKey: string;
}

export function getSupabaseCredentials(): SupabaseCredentials {
  const envUrl = import.meta.env.VITE_SUPABASE_URL || DEFAULT_SUPABASE_URL;
  const envKey =
    import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY ||
    import.meta.env.VITE_SUPABASE_ANON_KEY ||
    DEFAULT_SUPABASE_KEY;
  const localUrl = typeof window !== 'undefined' ? localStorage.getItem(STORAGE_URL_KEY) || '' : '';
  const localKey = typeof window !== 'undefined' ? localStorage.getItem(STORAGE_ANON_KEY) || '' : '';

  return {
    url: (localUrl || envUrl).trim(),
    anonKey: (localKey || envKey).trim(),
  };
}

export function saveSupabaseCredentials(url: string, anonKey: string): void {
  if (typeof window === 'undefined') return;
  if (url.trim() && anonKey.trim()) {
    localStorage.setItem(STORAGE_URL_KEY, url.trim());
    localStorage.setItem(STORAGE_ANON_KEY, anonKey.trim());
  } else {
    localStorage.removeItem(STORAGE_URL_KEY);
    localStorage.removeItem(STORAGE_ANON_KEY);
  }
}

let cachedClient: SupabaseClient | null = null;
let cachedUrl = '';
let cachedKey = '';

export function getSupabaseClient(): SupabaseClient | null {
  const { url, anonKey } = getSupabaseCredentials();
  if (!url || !anonKey) {
    cachedClient = null;
    return null;
  }
  if (cachedClient && cachedUrl === url && cachedKey === anonKey) {
    return cachedClient;
  }
  try {
    cachedClient = createClient(url, anonKey, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
      realtime: {
        params: {
          eventsPerSecond: 20,
        },
      },
    });
    cachedUrl = url;
    cachedKey = anonKey;
    return cachedClient;
  } catch {
    return null;
  }
}

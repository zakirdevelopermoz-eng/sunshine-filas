const CACHE_NAME = 'sunshine-pwa-v2';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  // Never intercept cross-origin requests (e.g. Supabase REST/Realtime) or API calls
  if (
    event.request.method !== 'GET' ||
    !event.request.url.startsWith(self.location.origin) ||
    event.request.url.includes('/api/')
  ) {
    return;
  }
});

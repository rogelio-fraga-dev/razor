// Service worker do painel Razor — SÓ a casca do app (app shell).
//
// Regras, em ordem de importância:
//  1. /api NUNCA passa por aqui: nem cache, nem respondWith. Dado de agenda/caixa velho na tela
//     é pior do que erro de rede — o barbeiro cobraria o valor errado.
//  2. Navegação (abrir /agenda, /b/slug…): rede primeiro; offline cai no index.html guardado.
//     Rede primeiro garante que um deploy novo chega na hora (o nginx serve index.html com
//     no-cache) e que o site público /b/:slug nunca fica preso numa versão antiga.
//  3. /assets/* (nome com hash do Vite, imutável): cache primeiro — um hash nunca muda de conteúdo.
//  4. Ícones/manifest/fontes do próprio domínio: stale-while-revalidate.
// Trocar VERSION descarta os caches antigos no `activate`.
const VERSION = "razor-shell-v2";
const SHELL = ["/", "/index.html", "/manifest.webmanifest", "/favicon.svg", "/icons/icon-192.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(VERSION)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // Google Fonts etc.: o navegador decide
  if (url.pathname.startsWith("/api/") || url.pathname === "/api") return; // regra 1

  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).catch(() =>
        caches.match("/index.html").then((r) => r || caches.match("/")).then((r) => r || Response.error()),
      ),
    );
    return;
  }

  if (url.pathname.startsWith("/assets/")) {
    event.respondWith(
      caches.match(req).then(
        (hit) =>
          hit ||
          fetch(req).then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(VERSION).then((c) => c.put(req, copy));
            }
            return res;
          }),
      ),
    );
    return;
  }

  if (url.pathname.startsWith("/icons/") || url.pathname === "/favicon.svg" || url.pathname === "/manifest.webmanifest") {
    event.respondWith(
      caches.match(req).then((hit) => {
        const net = fetch(req)
          .then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(VERSION).then((c) => c.put(req, copy));
            }
            return res;
          })
          .catch(() => hit);
        return hit || net;
      }),
    );
  }
});

// Registro do service worker (PWA). Só em build de produção: no `npm run dev` um SW guardando a
// casca do app brigaria com o hot reload do Vite e serviria arquivos velhos ao desenvolvedor.
// O sw.js nunca toca em /api (ver public/sw.js) — dado do painel é sempre ao vivo.
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD) return;
  if (!("serviceWorker" in navigator)) return;
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => {
      /* sem SW o painel funciona igual, só não abre offline */
    });
  });
}

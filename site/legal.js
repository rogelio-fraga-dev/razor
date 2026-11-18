// RAZOR · páginas legais (termos, privacidade, cancelamento).
//
// Só o necessário: o tema escolhido na landing (mesma chave do script.js) e o link para o
// painel. Arquivo próprio porque o script.js da landing espera os elementos da home — e a
// CSP (script-src 'self') não aceita script inline.
const THEME_KEY = "razor.site.theme";
function readTheme() { try { return localStorage.getItem(THEME_KEY); } catch { return null; } }
function saveTheme(v) { try { localStorage.setItem(THEME_KEY, v); } catch { /* modo privado */ } }
function applyTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", t === "light" ? "#F6F7F9" : "#0A0A0C");
}
applyTheme(readTheme() || "dark");
document.getElementById("theme-toggle")?.addEventListener("click", () => {
  const next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
  applyTheme(next);
  saveTheme(next);
});

// Mesmo endereço do painel que a landing usa (config.js reescrito no boot do container).
const PANEL = (() => {
  const configured = window.RAZOR_CONFIG?.panelUrl;
  if (configured) return configured;
  const host = location.hostname;
  return host === "localhost" || host === "127.0.0.1" || host === "" ? "http://localhost:5173" : "/app";
})();
document.querySelectorAll("[data-panel]").forEach((el) => el.setAttribute("href", PANEL));

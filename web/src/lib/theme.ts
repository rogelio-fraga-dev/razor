const KEY = "razor.theme";
export type Theme = "light" | "dark";

/** Cor da barra do sistema (Android/PWA instalado) por tema — o --bg de cada um. */
const THEME_COLOR: Record<Theme, string> = { dark: "#0A0A0C", light: "#F6F7F9" };

export function currentTheme(): Theme {
  let saved: Theme | null = null;
  try {
    saved = localStorage.getItem(KEY) as Theme | null;
  } catch {
    /* storage bloqueado: segue o padrão */
  }
  if (saved) return saved;
  // Dark-first (brand book v2): sem preferência salva, o padrão é dark.
  return "dark";
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute("data-theme", theme);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", THEME_COLOR[theme]);
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    /* storage bloqueado: o tema vale só nesta aba */
  }
}

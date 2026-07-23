const KEY = "razor.theme";
export type Theme = "light" | "dark";

export function currentTheme(): Theme {
  const saved = localStorage.getItem(KEY) as Theme | null;
  if (saved) return saved;
  // Dark-first (brand book v2): sem preferência salva, o padrão é dark.
  return "dark";
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem(KEY, theme);
}

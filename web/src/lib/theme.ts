const KEY = "razor.theme";
export type Theme = "light" | "dark";

export function currentTheme(): Theme {
  const saved = localStorage.getItem(KEY) as Theme | null;
  if (saved) return saved;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem(KEY, theme);
}

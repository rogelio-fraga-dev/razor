const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function money(value: number | null | undefined): string {
  return BRL.format(value ?? 0);
}

export function percent(value: number | null | undefined): string {
  const n = value ?? 0;
  return `${Number.isInteger(n) ? n : n.toFixed(1).replace(".", ",")}%`;
}

/** "2026-07-21" (yyyy-mm-dd) do dia local — usado como valor padrão dos filtros de data. */
export function todayIso(): string {
  const d = new Date();
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60000).toISOString().slice(0, 10);
}

export function dateBr(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

export function timeShort(t: string | null): string {
  return t ? t.slice(0, 5) : "—";
}

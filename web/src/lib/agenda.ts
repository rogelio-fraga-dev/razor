// Contas puras da agenda interativa: minutos, datas e a distribuição em "pistas" de blocos que
// se sobrepõem. Sem React aqui — fica fácil de ler e de testar na cabeça.
import type { AgendaItem, ConfirmationStatus } from "../api/types";

export const SLOT_MIN = 30;
export const PX_PER_MIN = 1.5; // 30min = 45px

export function hhmmToMin(t: string | null | undefined): number | null {
  if (!t) return null;
  const [h, m] = t.split(":").map(Number);
  if (Number.isNaN(h)) return null;
  return h * 60 + (m || 0);
}

export function minToHhmm(min: number): string {
  return `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;
}

export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Segunda-feira da semana de `iso`. */
export function weekStart(iso: string): string {
  const d = new Date(`${iso}T12:00:00`);
  const dow = d.getDay(); // 0=domingo
  return addDays(iso, dow === 0 ? -6 : 1 - dow);
}

/** Dia da semana ISO (1=segunda … 7=domingo) — o mesmo do backend (V32). */
export function isoWeekday(iso: string): number {
  const dow = new Date(`${iso}T12:00:00`).getDay();
  return dow === 0 ? 7 : dow;
}

/** Dia da semana 0=domingo … 6=sábado — a convenção dos bloqueios recorrentes (V16). */
export function jsWeekday(iso: string): number {
  return new Date(`${iso}T12:00:00`).getDay();
}

export function initials(name: string | null | undefined): string {
  const parts = (name ?? "").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

/**
 * Janela visível da grade: o expediente do tenant, alargada para caber qualquer agendamento que
 * esteja fora dele (marcado antes de mudar o horário, por exemplo) — senão o bloco sumiria.
 */
export function gridRange(
  opening: string | null,
  closing: string | null,
  items: AgendaItem[],
): { start: number; end: number } {
  let start = hhmmToMin(opening) ?? 9 * 60;
  let end = hhmmToMin(closing) ?? 19 * 60;
  for (const a of items) {
    const s = hhmmToMin(a.time);
    if (s === null) continue;
    start = Math.min(start, s);
    end = Math.max(end, s + (a.durationMinutes || SLOT_MIN));
  }
  start = Math.floor(start / 60) * 60;
  end = Math.ceil(end / 60) * 60;
  if (end <= start) end = start + 60;
  return { start, end };
}

/**
 * Distribui blocos que se sobrepõem em pistas lado a lado (dado legado pode ter dois horários do
 * mesmo barbeiro colidindo; empilhar um sobre o outro esconderia um deles).
 */
export function layoutLanes(items: AgendaItem[]): Map<number, { lane: number; lanes: number }> {
  const sorted = [...items]
    .filter((a) => hhmmToMin(a.time) !== null)
    .sort((a, b) => hhmmToMin(a.time)! - hhmmToMin(b.time)!);
  const out = new Map<number, { lane: number; lanes: number }>();
  let cluster: { id: number; end: number; lane: number }[] = [];
  let clusterEnd = -1;

  const flush = () => {
    const lanes = Math.max(1, ...cluster.map((c) => c.lane + 1));
    cluster.forEach((c) => out.set(c.id, { lane: c.lane, lanes }));
    cluster = [];
  };

  for (const a of sorted) {
    const s = hhmmToMin(a.time)!;
    const e = s + (a.durationMinutes || SLOT_MIN);
    if (s >= clusterEnd && cluster.length) flush();
    const used = new Set(cluster.filter((c) => c.end > s).map((c) => c.lane));
    let lane = 0;
    while (used.has(lane)) lane++;
    cluster.push({ id: a.id, end: e, lane });
    clusterEnd = Math.max(clusterEnd, e);
  }
  if (cluster.length) flush();
  return out;
}

/** "(11) 99999-0000" a partir de 5511999990000. */
export function phoneBr(raw: string | null | undefined): string {
  if (!raw) return "—";
  let d = raw.replace(/\D/g, "");
  if (d.startsWith("55") && d.length >= 12) d = d.slice(2);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return raw;
}

// ---------- Confirmação da véspera / sinal (V42) ----------


/** Texto e ícone da confirmação "1 confirma / 2 desmarca" enviada na véspera. */
export const CONFIRMATION_INFO: Record<ConfirmationStatus, { icon: string; label: string; cls: string }> = {
  PENDING: { icon: "⏳", label: "Aguardando confirmação", cls: "is-pending" },
  CONFIRMED: { icon: "👍", label: "Confirmou presença", cls: "is-confirmed" },
  DECLINED: { icon: "✕", label: "Desmarcou", cls: "is-noresp" },
  NO_RESPONSE: { icon: "⚠", label: "Não confirmou", cls: "is-noresp" },
};

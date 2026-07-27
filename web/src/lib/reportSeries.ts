// Série de faturamento do gráfico do painel. O backend devolve os pontos prontos numa única
// chamada (`/api/admin/reports/revenue-series`) — quem define as faixas de data é ele, que é
// dono do calendário e varre os agendamentos uma vez só.
import { api } from "../api/client";

export type SeriesPeriod = "7D" | "30D" | "MÊS";

export interface SeriesPoint {
  label: string;
  value: number;
}

/** Ponto como o backend devolve. */
interface RevenuePoint {
  label: string;
  from: string;
  to: string;
  revenue: number;
}

const BACKEND_PERIOD: Record<SeriesPeriod, string> = {
  "7D": "LAST_7_DAYS",
  "30D": "LAST_30_DAYS",
  "MÊS": "LAST_6_MONTHS",
};

/** yyyy-mm-dd do dia local (o backend espera data ISO sem fuso). */
function iso(d: Date): string {
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60000).toISOString().slice(0, 10);
}

/** Primeiro dia do mês corrente, em ISO — usado nos KPIs "do mês". */
export function firstDayOfMonth(today = new Date()): string {
  return iso(new Date(today.getFullYear(), today.getMonth(), 1));
}

export async function fetchRevenueSeries(period: SeriesPeriod): Promise<SeriesPoint[]> {
  const points = await api.get<RevenuePoint[]>(
    `/api/admin/reports/revenue-series?period=${BACKEND_PERIOD[period]}`,
  );
  return points.map((p) => ({ label: p.label, value: p.revenue }));
}

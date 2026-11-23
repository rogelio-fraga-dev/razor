// Chamadas do console da plataforma (V51). Sempre com ignore401: a sessão do console é outra —
// um 401 aqui mostra o login do console, nunca derruba a sessão do painel da barbearia.
import { api } from "../api/client";

const opts = { ignore401: true };

export const papi = {
  get: <T>(path: string) => api.get<T>(`/api/platform${path}`, opts),
  post: <T>(path: string, body?: unknown) => api.post<T>(`/api/platform${path}`, body, opts),
  put: <T>(path: string, body?: unknown) => api.put<T>(`/api/platform${path}`, body, opts),
  del: <T>(path: string) => api.del<T>(`/api/platform${path}`, opts),
};

export interface ShopRow {
  id: string;
  name: string;
  slug: string;
  city: string | null;
  status: string;
  active: boolean;
  trialEndsAt: string | null;
  createdAt: string | null;
  plan: string | null;
  whatsappConfigured: boolean;
  customers: number;
  appointments: number;
  botAppointments: number;
  messages: number;
  aiCalls: number;
  aiFailures: number;
  aiTokens: number;
  aiCostUsd: number;
  aiCostBrl: number;
  openIncidents: number;
}

export interface Overview {
  days: number;
  shops: number;
  byStatus: Record<string, number>;
  messages: number;
  botAppointments: number;
  aiCalls: number;
  aiCostUsd: number;
  aiCostBrl: number;
  usdToBrl: number;
  rows: ShopRow[];
}

export interface IncidentRow {
  id: string;
  type: string;
  status: "OPEN" | "RESOLVED";
  barbershopId: string | null;
  barbershopName: string | null;
  message: string;
  detail: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  resolvedAt: string | null;
  occurrences: number;
}

export interface AiView {
  secretsEnabled: boolean;
  keySource: "ENV" | "CONSOLE";
  keyLast4: string | null;
  chatModel: string;
  escalationModel: string;
  transcribeModel: string;
  envChatModel: string;
  envEscalationModel: string;
  envTranscribeModel: string;
  updatedAt: string | null;
}

export type ConversationFilter = "ALL" | "AI_ERROR" | "BOOKING_FAILED" | "ANGRY" | "FLAGGED";

export interface ConversationRow {
  customerId: string;
  shopId: string;
  shopName: string;
  customerName: string | null;
  phoneMasked: string;
  lastAt: string;
  messages: number;
  aiErrors: number;
  bookingFailures: number;
  angry: boolean;
  flagged: boolean;
  flagNote: string | null;
}

export interface ConversationDetail {
  customerId: string;
  shopId: string;
  shopName: string;
  customerName: string | null;
  phone: string;
  messages: { type: "USER" | "BOT"; content: string; at: string }[];
  events: { kind: string; detail: string | null; at: string }[];
  flagged: boolean;
  flagNote: string | null;
}

export interface DayPoint {
  day: string;
  messagesIn: number;
  messagesOut: number;
  botAppointments: number;
  aiCalls: number;
  aiFailures: number;
  aiErrorEvents: number;
  bookingFailures: number;
  avgResponseSeconds: number | null;
}

export interface RevenueEntry {
  id: string;
  month: string;
  barbershopId: string | null;
  barbershopName: string | null;
  plan: string | null;
  amount: number;
  note: string | null;
}

export interface Revenue {
  currentMonth: number;
  previousMonth: number;
  byPlanThisMonth: Record<string, number>;
  months: { month: string; total: number }[];
  entries: RevenueEntry[];
}

export interface AuditEntry {
  id: string;
  userEmail: string | null;
  action: string;
  barbershopId: string | null;
  barbershopName: string | null;
  target: string | null;
  ip: string | null;
  createdAt: string;
}

export function dt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export const brl = (v: number) => v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const usd = (v: number) => v.toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 4 });

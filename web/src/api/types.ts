// Tipos espelhando os DTOs da API do painel (/api/admin).

export type UserRole = "OWNER" | "RECEPTION" | "BARBER";

export interface LoginResponse {
  token: string;
  barbershopId: string;
  role: UserRole;
  name: string | null;
  email: string;
}

export interface ServiceOffering {
  id: string;
  name: string;
  price: number;
  durationMinutes: number;
  active: boolean;
}

export interface ServiceInput {
  name: string;
  price: number;
  durationMinutes: number;
  active?: boolean;
}

export interface Professional {
  id: string;
  name: string;
  phone: string | null;
  active: boolean;
  commissionPercent: number;
}

export interface ProfessionalInput {
  name: string;
  phone?: string | null;
  active?: boolean;
  commissionPercent?: number | null;
}

export interface Barbershop {
  id: string;
  name: string;
  slug: string;
  whatsappInstance: string | null;
  active: boolean;
}

export interface AdminContact {
  id: string;
  phone: string;
  name: string | null;
}

export interface Customer {
  id: string;
  name: string | null;
  phone: string;
  fidelityPoints: number;
  lastServiceDate: string | null;
  createdAt: string | null;
  planId: string | null;
  planName: string | null;
}

export type WhatsAppState = "open" | "connecting" | "close" | "unknown";

/** Folga / bloqueio de agenda. `date` (pontual) OU `weekday` (0=dom … 6=sáb, recorrente).
 *  Horários nulos = dia inteiro. `professionalId` nulo = barbearia inteira. */
export interface ScheduleBlock {
  id: string;
  professionalId: string | null;
  professionalName: string | null;
  date: string | null;
  weekday: number | null;
  startTime: string | null;
  endTime: string | null;
  reason: string | null;
}

export interface BotStatus {
  instanceName: string;
  whatsappState: WhatsAppState;
  whatsappConnected: boolean;
  aiConfigured: boolean;
  botEnabled: boolean;
}

export interface Me {
  userId: string;
  email: string;
  name: string | null;
  barbershopId: string;
  role: UserRole;
}

export interface WhatsAppStatus {
  instanceName: string;
  state: WhatsAppState;
  connected: boolean;
}

export interface WhatsAppConnect {
  instanceName: string;
  state: WhatsAppState;
  qrCodeBase64: string | null;
  pairingCode: string | null;
}

export interface BotSettings {
  displayName: string | null;
  addressText: string | null;
  addressMapsUrl: string | null;
  servicesText: string | null;
  scheduleUrl: string | null;
  instagramUrl: string | null;
  pixKey: string | null;
  pixCopyPaste: string | null;
  paymentInfo: string | null;
  openingTime: string | null;
  closingTime: string | null;
  workingDays: string | null;
  welcomeText: string | null;
  reminderText: string | null;
  reviewText: string | null;
  fidelityRewardText: string | null;
  cancellationPolicy: string | null;
  botPersona: string | null;
}

export interface SubscriptionPlan {
  id: string;
  name: string;
  price: number;
  description: string | null;
  active: boolean;
}

export type PaymentStatus = "PIX" | "PRESENCIAL" | "PENDENTE";

export interface AppointmentView {
  time: string | null;
  customerName: string | null;
  customerPhone: string | null;
  service: string | null;
  durationMinutes: number | null;
  paymentStatus: PaymentStatus | null;
  professionalId: string | null;
  professionalName: string | null;
  subscriber: boolean;
}

export interface Bucket {
  count: number;
  value: number;
}

export interface FinancialSummary {
  from: string;
  to: string;
  totalAppointments: number;
  categories: { combo: number; barba: number; simples: number };
  payments: { pix: Bucket; presencial: Bucket; pendente: Bucket };
  totalRevenue: number;
  averageTicket: number;
}

export interface CommissionItem {
  professionalId: string;
  professionalName: string;
  commissionPercent: number;
  atendimentos: number;
  faturamento: number;
  comissao: number;
}

export interface CommissionReport {
  date: string;
  items: CommissionItem[];
  total: number;
}

// ---------- Caixa (V18) ----------

export type CashMovementType = "VENDA" | "SANGRIA" | "SUPRIMENTO";
export type CashPaymentMethod = "DINHEIRO" | "PIX" | "DEBITO" | "CREDITO";

export interface CashMovementView {
  id: string;
  type: CashMovementType;
  paymentMethod: CashPaymentMethod | null;
  amount: number;
  description: string | null;
  createdAt: string;
}

export interface CashTotals {
  vendasDinheiro: number;
  vendasPix: number;
  vendasDebito: number;
  vendasCredito: number;
  vendasTotal: number;
  sangrias: number;
  suprimentos: number;
}

/** Retrato da sessão de caixa. Endpoint devolve `null` quando o caixa está fechado. */
export interface CashSessionSummary {
  id: string;
  status: "OPEN" | "CLOSED";
  openedByName: string | null;
  openedAt: string;
  openingAmount: number;
  closedByName: string | null;
  closedAt: string | null;
  closingCounted: number | null;
  closingExpected: number | null;
  notes: string | null;
  movements: CashMovementView[];
  totals: CashTotals;
  expectedDrawer: number;
  difference: number | null;
}

export interface CashMovementInput {
  type: CashMovementType;
  paymentMethod?: CashPaymentMethod | null;
  amount: number;
  description?: string | null;
}

// ---------- Metas (V19) ----------

/** Meta de faturamento do mês definida pelo dono. `null` = sem meta (o painel omite o card). */
export interface Goals {
  monthlyRevenueGoal: number | null;
}

// ---------- Produtos de balcão (V20) ----------

/** Produto vendido no balcão. Sem duração: não ocupa cadeira nem entra na agenda. */
export interface Product {
  id: string;
  name: string;
  price: number;
  active: boolean;
}

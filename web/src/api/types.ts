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
  /** `null` = usa o percentual dos serviços (V23) — diferente de 0, que é "não paga". */
  productCommissionPercent: number | null;
}

export interface ProfessionalInput {
  name: string;
  phone?: string | null;
  active?: boolean;
  commissionPercent?: number | null;
  productCommissionPercent?: number | null;
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
  /** Só dos **agendamentos** — a forma de pagamento das vendas de produto vive no caixa. */
  payments: { pix: Bucket; presencial: Bucket; pendente: Bucket };
  /** Faturamento dos agendamentos. */
  servicesRevenue: number;
  /** Unidades vendidas (balcão V21 + comanda V22). */
  productsSold: number;
  productsRevenue: number;
  /** Serviços + produtos: o faturamento da barbearia no período. */
  totalRevenue: number;
  /** Serviços ÷ atendimentos — produto fica fora para não inflar o ticket. */
  averageTicket: number;
}

export interface CommissionItem {
  professionalId: string;
  professionalName: string;
  commissionPercent: number;
  atendimentos: number;
  /** Faturamento dos **serviços**; produtos do balcão vêm separados (V21). */
  faturamento: number;
  productCommissionPercent: number;
  produtos: number;
  faturamentoProdutos: number;
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
  /** Estoque é opt-in (V24): desligado, a venda nunca é bloqueada por saldo. */
  trackStock: boolean;
  stockQuantity: number;
  minStock: number;
  lowStock: boolean;
}

export type StockMovementType = "ENTRADA" | "SAIDA" | "AJUSTE";

export interface StockMovement {
  id: string;
  type: StockMovementType;
  quantity: number;
  resultingStock: number;
  note: string | null;
  createdAt: string;
}

/** Venda de balcão (V21). Nome e preço são os congelados no momento da venda. */
export interface ProductSale {
  id: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  total: number;
  professionalId: string | null;
  professionalName: string | null;
  paymentMethod: CashPaymentMethod;
  soldAt: string;
}

// ---------- Comanda por atendimento (V22) ----------

export type ComandaStatus = "ABERTA" | "FECHADA" | "CANCELADA";
export type ComandaItemKind = "SERVICO" | "PRODUTO";

export interface ComandaItem {
  id: string;
  kind: ComandaItemKind;
  referenceId: string | null;
  description: string;
  unitPrice: number;
  quantity: number;
  total: number;
}

/** `currentTotal` é a soma do que está na conta agora; `total` só existe depois de fechada. */
export interface Comanda {
  id: string;
  customerName: string | null;
  customerPhone: string | null;
  professionalId: string | null;
  professionalName: string | null;
  status: ComandaStatus;
  openedAt: string;
  closedAt: string | null;
  currentTotal: number;
  total: number | null;
  paymentMethod: CashPaymentMethod | null;
  items: ComandaItem[];
}

// ---------- Comissões pagas (V25) ----------

export interface CommissionSettlementItem {
  professionalId: string;
  professionalName: string;
  atendimentos: number;
  faturamento: number;
  produtos: number;
  faturamentoProdutos: number;
  comissao: number;
  /** Já pago referente a este intervalo (só acertos contidos nele). */
  pago: number;
  /** Vales com data dentro do intervalo (V26). */
  vales: number;
  /** comissão − pago − vales. **Pode ser negativo**: o barbeiro deve à barbearia. */
  aReceber: number;
}

export interface CommissionSettlement {
  from: string;
  to: string;
  items: CommissionSettlementItem[];
  totalComissao: number;
  totalPago: number;
  totalVales: number;
  totalAReceber: number;
}

export interface CommissionPayment {
  id: string;
  professionalId: string;
  professionalName: string | null;
  periodStart: string;
  periodEnd: string;
  amount: number;
  /** True = saiu da gaveta (gerou sangria); false = pago por fora do caixa. */
  fromCashRegister: boolean;
  notes: string | null;
  paidByName: string | null;
  paidAt: string;
}

/** Vale/adiantamento ao barbeiro (V26), descontado da comissão no acerto do período. */
export interface Advance {
  id: string;
  professionalId: string;
  professionalName: string | null;
  amount: number;
  advanceDate: string;
  fromCashRegister: boolean;
  notes: string | null;
  grantedByName: string | null;
}

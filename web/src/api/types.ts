// Tipos espelhando os DTOs da API do painel (/api/admin).

export type UserRole = "OWNER" | "RECEPTION" | "BARBER";

export interface LoginResponse {
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
  /** Data URI (`data:image/jpeg;base64,…`) pronto para `<img src>` — V37. `null` = sem foto. */
  photoUrl: string | null;
  /** Como o bot chama o barbeiro com o cliente. */
  nickname: string | null;
  /** Apelido quando existe, senão o nome. */
  displayName: string;
  bio: string | null;
  specialties: string[];
  instagramUrl: string | null;
  /** Serviços que atende. Vazio = todos. */
  serviceIds: string[];
}

/**
 * Nos campos de perfil, `undefined`/`null` = "não mexe" e vazio (`""`/`[]`) = "limpa".
 */
export interface ProfessionalInput {
  name: string;
  phone?: string | null;
  active?: boolean;
  commissionPercent?: number | null;
  productCommissionPercent?: number | null;
  photoUrl?: string | null;
  nickname?: string | null;
  bio?: string | null;
  specialties?: string[] | null;
  instagramUrl?: string | null;
  serviceIds?: string[] | null;
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
  /** Dias abertos, ISO 1=seg…7=dom (V32). Fonte da verdade do expediente. */
  workingWeekdays: Weekday[];
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
  /** Antecedência com que o assinante pode agendar (V27).  = sem regra especial. */
  bookingWindowDays: number | null;
}

export type BenefitTargetType = "SERVICO" | "PRODUTO";

/** Benefício de plano (V27).  nulo = vale para todos do tipo. */
export interface PlanBenefit {
  id: string;
  targetType: BenefitTargetType;
  targetId: string | null;
  discountPercent: number;
  monthlyQuota: number | null;
  /** CSV ISO (1=segunda … 7=domingo).  = todos os dias. */
  weekdays: string | null;
  free: boolean;
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
  /** Agendamento que originou a comanda (V28). null = atendimento de balcão. */
  appointmentId: number | null;
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

// ---------- Cupons (V29) ----------

export type CouponDiscountType = "PERCENT" | "FIXED";
export type CouponOrigin = "MANUAL" | "FIDELIDADE";

/** Cupom de desconto. `customerPhone` preenchido = pessoal; nulo = campanha aberta. */
export interface Coupon {
  id: string;
  code: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: number;
  origin: CouponOrigin;
  customerPhone: string | null;
  validFrom: string | null;
  validUntil: string | null;
  maxUses: number | null;
  usedCount: number;
  minAmount: number | null;
  active: boolean;
  /** Dá para usar hoje? Junta validade, limite de usos e o liga/desliga. */
  usable: boolean;
}

// ---------- Pacotes pré-pagos (V34) ----------

/** Pacote do catálogo. `serviceId` nulo = cobre qualquer serviço. */
export interface ServicePackage {
  id: string;
  name: string;
  serviceId: string | null;
  serviceName: string | null;
  quantity: number;
  price: number;
  unitValue: number;
  validityDays: number;
  active: boolean;
}

export type CustomerPackageStatus = "ATIVO" | "ESGOTADO" | "VENCIDO";

/** Pacote vendido a um cliente, com o saldo de usos. */
export interface CustomerPackage {
  id: string;
  customerId: string;
  customerName: string | null;
  customerPhone: string | null;
  packageId: string | null;
  packageName: string;
  serviceId: string | null;
  serviceName: string | null;
  totalUses: number;
  remainingUses: number;
  price: number;
  unitValue: number;
  paymentMethod: CashPaymentMethod;
  purchasedAt: string;
  expiresAt: string;
  status: CustomerPackageStatus;
}

// ---------- Contas a pagar e DRE (V31) ----------

export type ExpenseCategory =
  | "ALUGUEL"
  | "ENERGIA"
  | "AGUA"
  | "INTERNET"
  | "PRODUTOS"
  | "SALARIOS"
  | "IMPOSTOS"
  | "MARKETING"
  | "MANUTENCAO"
  | "SOFTWARE"
  | "OUTROS";

/** Opção de categoria com o rótulo pronto — vem do backend para não duplicar a lista aqui. */
export interface ExpenseCategoryOption {
  value: ExpenseCategory;
  label: string;
}

/**
 * Conta a pagar. `paid` e `overdue` vêm calculados do servidor de propósito: são a mesma
 * pergunta que o DRE responde, e deixar o navegador decidir "está vencida?" abriria espaço
 * para as duas telas discordarem por causa do fuso.
 *
 * `fromCashRegister` = o pagamento saiu da gaveta; é o que explica por que a conta não pode
 * mais ser editada nem apagada.
 */
export interface Expense {
  id: string;
  description: string;
  category: ExpenseCategory;
  categoryLabel: string;
  amount: number;
  dueDate: string;
  paidAt: string | null;
  paid: boolean;
  overdue: boolean;
  recurring: boolean;
  fromCashRegister: boolean;
  notes: string | null;
}

export interface DreExpenseLine {
  category: ExpenseCategory;
  label: string;
  amount: number;
  /** Quanto essa categoria come do faturamento. Nulo quando não houve receita. */
  shareOfRevenue: number | null;
}

/**
 * DRE do período. Despesa entra por **competência** (vencimento), não por desembolso — por
 * isso `expensesPaid` e `expensesUnpaid` somam o mesmo `totalExpenses`, mas dizem quanto
 * daquilo já saiu de fato.
 *
 * `marginPercent` é nulo sem receita: mostrar 0% sugeriria margem nula quando o certo é
 * "não há o que medir".
 */
export interface Dre {
  from: string;
  to: string;
  servicesRevenue: number;
  productsRevenue: number;
  totalRevenue: number;
  expenses: DreExpenseLine[];
  totalExpenses: number;
  expensesPaid: number;
  expensesUnpaid: number;
  overdueAmount: number;
  result: number;
  marginPercent: number | null;
}

// ---------- Paginação ----------

/**
 * Envelope das listas paginadas. `total` é a contagem da base inteira (com o filtro aplicado),
 * não da página — é o que permite a tela dizer "21–40 de 412" sem inventar número.
 */
export interface PageMeta<T = unknown> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
}

/** Totais da base de clientes, contados no servidor (não dão para deduzir de uma página). */
export interface CustomerSummary {
  total: number;
  subscribers: number;
  newInWindow: number;
}

// ---------- Expediente e ocupação ----------

/** Dia da semana ISO: 1=segunda … 7=domingo (igual a java.time.DayOfWeek). */
export type Weekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export interface ProfessionalOccupancy {
  professionalId: string;
  professionalName: string;
  availableHours: number;
  bookedHours: number;
  /** Nulo quando não houve hora disponível — não é 0%, é "não há o que medir". */
  rate: number | null;
  appointments: number;
}

/**
 * Taxa de ocupação das cadeiras. `availableHours` já desconta dias fechados (V32) e folgas (V16);
 * `bookedHours` usa a duração real de cada serviço, não a contagem de atendimentos.
 */
export interface Occupancy {
  from: string;
  to: string;
  openDays: number;
  availableHours: number;
  bookedHours: number;
  rate: number | null;
  unassignedAppointments: number;
  professionals: ProfessionalOccupancy[];
}

/**
 * Chaves de BotSettings que são texto. `workingWeekdays` fica de fora por ser a única lista —
 * separar impede que os helpers de campo de texto a aceitem por engano.
 */
export type BotTextKey = Exclude<keyof BotSettings, "workingWeekdays">;

// ---------- Retenção ----------

/** Cliente que merece um contato. O ritmo esperado é o dele, não um prazo fixo da casa. */
export interface CustomerAtRisk {
  customerId: string;
  name: string | null;
  phone: string;
  lastVisit: string | null;
  daysSinceLastVisit: number;
  expectedIntervalDays: number;
  visits: number;
  subscriber: boolean;
}

/**
 * Retenção da base. O status é relativo ao ritmo de cada cliente: quem corta a cada 15 dias e
 * sumiu há 40 é um problema; quem corta a cada 60 e sumiu há 40 está em dia.
 *
 * `medianIntervalDays` é nulo quando ninguém da base tem ritmo utilizável; aí vale
 * `defaultIntervalDays`.
 */
export interface Retention {
  referenceDate: string;
  totalCustomers: number;
  counts: { novo: number; ativo: number; emRisco: number; sumido: number };
  medianIntervalDays: number | null;
  defaultIntervalDays: number;
  returnRate: number | null;
  atRisk: CustomerAtRisk[];
  lost: CustomerAtRisk[];
}

// ---------- Site white-label (V33) ----------

export type SiteTemplate = "CLASSICO" | "ESTUDIO" | "VITRINE";
export type SiteTheme = "LIGHT" | "DARK" | "AUTO";

/**
 * O site como o VISITANTE o recebe — espelha PublicSiteResponse.
 *
 * `price` nulo quando o dono optou por não publicar preços. `team` traz só o perfil público:
 * telefone e comissão do barbeiro não existem nesta rota, por decisão do backend.
 */
/** Barbeiro como vitrine (V37) — só campos que o dono preencheu para aparecer. */
export interface PublicTeamMember {
  name: string;
  nickname: string | null;
  /** Sempre um data URI de imagem validado no backend (nunca outro esquema). */
  photoUrl: string | null;
  bio: string | null;
  specialties: string[];
  instagramUrl: string | null;
}

export interface PublicSite {
  slug: string;
  barbershopName: string;
  template: SiteTemplate;
  theme: SiteTheme;
  accentColor: string;
  headline: string | null;
  subheadline: string | null;
  aboutText: string | null;
  logoUrl: string | null;
  heroImageUrl: string | null;
  gallery: string[];
  whatsappNumber: string | null;
  instagramUrl: string | null;
  mapsUrl: string | null;
  addressText: string | null;
  /** Derivado dos dias estruturados (V32) — o site nunca anuncia expediente que a agenda recusa. */
  openingHours: string | null;
  services: { name: string; price: number | null; durationMinutes: number | null }[];
  team: PublicTeamMember[];
  plans: { name: string; price: number; description: string | null }[];
  /** Agendamento online ligado (V38): o "Agendar" leva a /b/:slug/agendar em vez do WhatsApp. */
  onlineBooking: boolean;
}

// ---------- Agendamento online (V38) — espelha PublicBookingDtos ----------

export interface BookingOptions {
  slug: string;
  barbershopName: string;
  theme: SiteTheme;
  accentColor: string;
  logoUrl: string | null;
  whatsappNumber: string | null;
  maxDaysAhead: number;
  /** ISO 1=seg … 7=dom. */
  openWeekdays: number[];
  services: { id: string; name: string; durationMinutes: number; price: number | null }[];
  professionals: { id: string; name: string }[];
}

export interface BookingSlots {
  date: string;
  slots: string[];
}

export interface BookingConfirmation {
  barbershopName: string;
  serviceName: string;
  professionalName: string | null;
  date: string;
  time: string;
}

// ---------- Agenda interativa (/api/admin/agenda) ----------

export type AttendanceStatus = "ATTENDED" | "NO_SHOW";

export interface AgendaPro {
  id: string;
  name: string;
  active: boolean;
  /** Foto do perfil do barbeiro, quando o cadastro tiver. Sem ela, a agenda usa as iniciais. */
  photoUrl?: string | null;
}

export interface AgendaItem {
  id: number;
  date: string;
  time: string | null;
  durationMinutes: number;
  customerName: string | null;
  customerPhone: string | null;
  service: string | null;
  professionalId: string | null;
  professionalName: string | null;
  paymentStatus: PaymentStatus | null;
  attendanceStatus: AttendanceStatus | null;
  subscriber: boolean;
  comandaId: string | null;
  comandaStatus: string | null;
}

export interface AgendaBlock {
  id: string;
  professionalId: string | null;
  date: string;
  startTime: string | null;
  endTime: string | null;
  reason: string | null;
}

export interface AgendaBoard {
  from: string;
  to: string;
  openingTime: string | null;
  closingTime: string | null;
  /** ISO 1=segunda … 7=domingo. */
  openWeekdays: number[];
  professionals: AgendaPro[];
  appointments: AgendaItem[];
  blocks: AgendaBlock[];
}

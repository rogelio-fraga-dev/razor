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

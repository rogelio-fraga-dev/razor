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

export type PaymentStatus = "PIX" | "PRESENCIAL" | "PENDENTE";

export interface AppointmentView {
  time: string | null;
  customerName: string | null;
  service: string | null;
  paymentStatus: PaymentStatus | null;
  professionalName: string | null;
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

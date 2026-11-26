// Tipos do cadastro pela landing e do assistente guiado (V49) — espelham SignupController e
// AdminOnboardingController.
import type { LoginResponse } from "./types";

/** Resposta do cadastro (POST /api/signup): já vem com a sessão aberta no cookie. */
export interface SignupResponse extends LoginResponse {
  slug: string;
  status: "TRIAL" | "ACTIVE";
  trialEndsAt: string | null;
}

export interface SignupInput {
  shopName: string;
  ownerName: string;
  email: string;
  password: string;
  whatsapp: string;
  city: string;
  plan: string | null;
  /** Honeypot: fica escondido; gente não preenche. */
  website: string;
  /** Código do link de convite (piloto). */
  invite?: string | null;
}

/** Passos do assistente guiado — contrato com OnboardingProgressService.STEPS. */
export type OnboardingStepId = "casa" | "horario" | "servicos" | "equipe" | "whatsapp" | "teste" | "site";

/** GET /api/admin/onboarding. */
export interface OnboardingProgress {
  slug: string;
  status: "TRIAL" | "ACTIVE";
  trialEndsAt: string | null;
  city: string | null;
  plan: string | null;
  steps: OnboardingStepId[];
  stepsDone: OnboardingStepId[];
  completed: boolean;
}

/** GET /api/admin/onboarding/bot-test — o "oi" de teste chegou? O bot respondeu? */
export interface BotTestStatus {
  lastCustomerMessageAt: string | null;
  lastBotMessageAt: string | null;
  received: boolean;
  replied: boolean;
}

/**
 * Marca de "acabou de se cadastrar" desta aba: a rota /cadastro usa para mandar o dono recém-logado
 * ao assistente (/comecar) e não ao Painel. Em memória de propósito — some ao recarregar.
 */
export const signupFlow = { done: false };

/** "2026-12-02T10:00:00" -> "02/12". Data local do servidor (fuso de SP), sem conversão. */
export function shortDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const [y, m, d] = iso.slice(0, 10).split("-");
  return y && m && d ? `${d}/${m}` : "";
}

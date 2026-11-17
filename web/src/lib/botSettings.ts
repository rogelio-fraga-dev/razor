import type { BotSettings, BotTextKey } from "../api/types";

/** Formulário vazio da config do bot — Seg a Sáb abertos por padrão. */
export const EMPTY_BOT: BotSettings = {
  displayName: "",
  addressText: "",
  addressMapsUrl: "",
  servicesText: "",
  scheduleUrl: "",
  instagramUrl: "",
  pixKey: "",
  pixCopyPaste: "",
  paymentInfo: "",
  openingTime: "",
  closingTime: "",
  workingWeekdays: [1, 2, 3, 4, 5, 6],
  welcomeText: "",
  reminderText: "",
  reviewText: "",
  fidelityRewardText: "",
  cancellationPolicy: "",
  botPersona: "",
};

/**
 * Preenche o formulário a partir do que veio do backend (campo nulo vira string vazia).
 *
 * Parte de `{...data}` de propósito: o PUT troca o registro inteiro, então qualquer campo que
 * o backend devolva e o formulário ainda não conheça precisa voltar intacto no save — senão o
 * modal apagaria em silêncio uma config criada depois dele.
 */
export function fillBot(data: BotSettings | null): BotSettings {
  const filled: BotSettings = { ...EMPTY_BOT, ...(data ?? {}) };
  if (!data) return filled;
  (Object.keys(EMPTY_BOT) as (keyof BotSettings)[]).forEach((k) => {
    if (k === "workingWeekdays") return;
    filled[k as BotTextKey] = data[k as BotTextKey] ?? "";
  });
  // A lista de dias não pode virar vazia: vazia significaria "fechado todo dia".
  filled.workingWeekdays = data.workingWeekdays?.length ? data.workingWeekdays : EMPTY_BOT.workingWeekdays;
  return filled;
}

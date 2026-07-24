// Os 5 modos de fala do bot. Cada modo é só um preset de texto para o campo `botPersona`,
// que já flui para o {{persona}} do system prompt (OpenAIService). Sem migração, sem coluna
// nova: escolher um modo apenas preenche a persona; "Personalizado" = texto livre.

export interface SpeechMode {
  id: string;
  label: string;
  tagline: string;
  /** Texto injetado como persona no prompt da IA. */
  persona: string;
}

export const SPEECH_MODES: SpeechMode[] = [
  {
    id: "classico",
    label: "Clássico",
    tagline: "Cordial e tradicional",
    persona:
      "Fale de forma cordial e respeitosa, tratando o cliente por \"senhor\" ou \"senhora\". " +
      "Tom profissional e tradicional de barbearia. Evite gírias e use no máximo um emoji por mensagem.",
  },
  {
    id: "parceiro",
    label: "Parceiro",
    tagline: "Próximo e amigável (padrão)",
    persona:
      "Fale de forma próxima e amigável, como um barbeiro que já conhece o cliente. " +
      "Trate por \"você\", com um tom de parceiro, leve e acolhedor. Use emojis com moderação.",
  },
  {
    id: "direto",
    label: "Direto",
    tagline: "Rápido e sem rodeios",
    persona:
      "Seja objetivo e rápido. Vá direto ao ponto, sem rodeios nem enrolação. " +
      "Frases curtas e claras. Quase nenhum emoji.",
  },
  {
    id: "premium",
    label: "Premium",
    tagline: "Sofisticado e exclusivo",
    persona:
      "Fale de forma sofisticada e atenciosa, transmitindo exclusividade e cuidado. " +
      "Linguagem elegante, tratando o cliente com distinção. Emojis sutis e pontuais.",
  },
  {
    id: "brincalhao",
    label: "Brincalhão",
    tagline: "Descontraído e bem-humorado",
    persona:
      "Fale de forma descontraída e bem-humorada, com gírias leves e um tom animado. " +
      "Deixe a conversa divertida sem perder a clareza. Pode usar mais emojis.",
  },
];

/** Modo padrão sugerido no onboarding. */
export const DEFAULT_SPEECH_MODE = "parceiro";

/** Qual modo corresponde ao texto atual da persona (null = Personalizado). */
export function matchSpeechMode(persona: string | null | undefined): string | null {
  const t = (persona ?? "").trim();
  if (!t) return null;
  return SPEECH_MODES.find((m) => m.persona.trim() === t)?.id ?? null;
}

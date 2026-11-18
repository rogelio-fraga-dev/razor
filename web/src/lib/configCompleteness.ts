import type { AdminContact, BotSettings, BotStatus } from "../api/types";

/**
 * O que precisa estar configurado para a barbearia operar de verdade.
 *
 * <p>Existe porque "configurado" não é uma coisa só. Antes o assistente abria sozinho apenas
 * quando o <b>nome</b> estava vazio — uma barbearia com nome preenchido e nenhum PIX, nenhum
 * horário e o WhatsApp desconectado era tratada como pronta, e ninguém avisava o dono.
 *
 * <p>A separação entre `required` e o resto importa: sem os obrigatórios o bot **não atende**;
 * sem os demais ele atende pior, mas atende. Misturar os dois faria a tela gritar sobre um
 * Instagram em branco com o mesmo peso de um WhatsApp desconectado.
 */
export interface ConfigItem {
  id: string;
  label: string;
  hint: string;
  done: boolean;
  /** Sem isto o bot não funciona. */
  required: boolean;
  /** Passo do assistente que resolve este item. */
  step: number;
}

export interface ConfigStatus {
  items: ConfigItem[];
  /** Quantos itens obrigatórios ainda faltam. */
  missingRequired: number;
  /** Quantos itens no total ainda faltam (obrigatórios + recomendados). */
  missing: number;
  /** 0–100, contando todos os itens. */
  percent: number;
  /** Pronto para operar: nenhum obrigatório pendente. */
  ready: boolean;
}

function filled(value: string | null | undefined): boolean {
  return (value ?? "").trim() !== "";
}

export function evaluateConfig(
  bot: BotSettings | null,
  status: BotStatus | null,
  admins: AdminContact[] | null,
  /** Serviços ativos no catálogo (V46): é deles que o bot tira preço e duração. */
  activeServices: number | null = null,
): ConfigStatus {
  const items: ConfigItem[] = [
    {
      id: "whatsapp",
      label: "WhatsApp conectado",
      hint: "É o número que o cliente vê. Sem ele o bot não recebe nem responde nada.",
      done: status?.whatsappConnected === true,
      required: true,
      step: 0,
    },
    {
      id: "admin",
      label: "Seu número de admin",
      hint: "Por onde você comanda o bot e recebe os avisos. Precisa ser diferente do número da barbearia.",
      done: (admins?.length ?? 0) > 0,
      required: true,
      step: 1,
    },
    {
      id: "name",
      label: "Nome da barbearia",
      hint: "O bot usa esse nome ao falar com o cliente.",
      done: filled(bot?.displayName),
      required: true,
      step: 2,
    },
    {
      id: "hours",
      label: "Horário e dias de funcionamento",
      hint: "Define quais horários a IA pode oferecer. Sem isso ela usa 09:00–19:00, de segunda a sábado.",
      done: filled(bot?.openingTime) && filled(bot?.closingTime) && (bot?.workingWeekdays?.length ?? 0) > 0,
      required: true,
      step: 3,
    },
    {
      id: "services",
      label: "Tabela de serviços e preços",
      hint: "O que o bot responde quando perguntam “quanto custa?”. Vazia, ele não inventa preço — diz que não sabe.",
      done: (activeServices ?? 0) > 0 || filled(bot?.servicesText),
      required: true,
      step: 4,
    },
    {
      id: "pix",
      label: "Chave PIX",
      hint: "Sem PIX configurado o bot não envia cobrança nenhuma.",
      done: filled(bot?.pixKey),
      required: false,
      step: 6,
    },
    {
      id: "address",
      label: "Endereço",
      hint: "O bot manda quando o cliente pergunta onde fica.",
      done: filled(bot?.addressText),
      required: false,
      step: 2,
    },
    {
      id: "persona",
      label: "Modo de fala da IA",
      hint: "O tom de voz do seu assistente. Em branco, ele usa o padrão “Parceiro”.",
      done: filled(bot?.botPersona),
      required: false,
      step: 5,
    },
    {
      id: "ai",
      label: "Inteligência artificial ativa",
      hint: "Chave da OpenAI configurada no servidor. Sem ela o bot só responde pelo menu numerado.",
      done: status?.aiConfigured === true,
      required: false,
      // Não é passo do assistente: é configuração de servidor, não do dono.
      step: -1,
    },
  ];

  const missing = items.filter((i) => !i.done).length;
  const missingRequired = items.filter((i) => !i.done && i.required).length;
  const done = items.length - missing;

  return {
    items,
    missing,
    missingRequired,
    percent: Math.round((done / items.length) * 100),
    ready: missingRequired === 0,
  };
}

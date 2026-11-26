// Arquitetura de informação do painel. Fonte única para o menu lateral, a navegação inferior do
// celular e a barra de comandos (Ctrl+K) — mudar aqui muda nos três.
//
// Duas camadas, pensadas pelo que o barbeiro faz no dia (não pelas telas do sistema):
// - ATALHOS: o que se abre várias vezes por dia (Painel, Agenda, Comandas, Clientes). Ficam
//   sempre à mostra, a 1 clique, no topo do menu e na barra inferior do celular.
// - GRUPOS: o resto, recolhido em blocos que abrem para baixo (Fidelização, Dinheiro,
//   Configurar). Fechados por padrão; o grupo da tela atual abre sozinho.
import type { ReactNode } from "react";
import type { UserRole } from "../api/types";
import { canAccess } from "./access";
import {
  IconBox,
  IconCalendar,
  IconCash,
  IconGem,
  IconGrid,
  IconHandshake,
  IconPercent,
  IconPulse,
  IconReceipt,
  IconSliders,
  IconSun,
  IconTag,
  IconTicket,
  IconTrendDown,
  IconUser,
  IconUsers,
  IconWallet,
} from "../components/Icons";

export type GroupId = "fidelizacao" | "dinheiro" | "configurar";

export interface NavPage {
  to: string;
  label: string;
  /** Uma linha em linguagem simples: aparece na folha do celular e no Ctrl+K. */
  hint: string;
  icon: ReactNode;
  /** NavLink `end`: necessário quando a rota é prefixo de outra (/commissions × /settlement). */
  end?: boolean;
  /**
   * Restringe a exibição NO MENU além do `canAccess`. O "Meu dia" é aberto ao dono (que também
   * corta cabelo) pela barra de comandos, mas no menu só aparece para o barbeiro — o atalho do
   * dono é o Painel.
   */
  menuRoles?: UserRole[];
  /** Função que o plano da barbearia precisa liberar (piloto sem clube e sem pacotes). */
  feature?: "CLUB" | "PACKAGES" | "LOYALTY";
}

// Funções desligadas pelo plano da barbearia (GET /api/admin/plan-features), lidas pelo Layout.
let disabledFeatures = new Set<string>();
export function setDisabledFeatures(list: string[]) {
  disabledFeatures = new Set(list);
}
export function featureEnabled(f: string | undefined): boolean {
  return !f || !disabledFeatures.has(f);
}

export interface NavGroup {
  id: GroupId;
  label: string;
  icon: ReactNode;
  pages: NavPage[];
}

/** Atalhos do dia a dia: sempre visíveis, nesta ordem. */
export const QUICK: NavPage[] = [
  { to: "/", label: "Painel", hint: "o dia da barbearia num olhar", icon: <IconGrid />, end: true },
  { to: "/meu-dia", label: "Meu dia", hint: "meus horários e quanto eu ganhei", icon: <IconSun />, menuRoles: ["BARBER"] },
  { to: "/agenda", label: "Agenda", hint: "horários por barbeiro", icon: <IconCalendar /> },
  { to: "/comandas", label: "Comandas", hint: "a conta de cada atendimento", icon: <IconReceipt /> },
  { to: "/customers", label: "Clientes", hint: "base, assinantes e histórico", icon: <IconUsers /> },
];

export const GROUPS: NavGroup[] = [
  {
    id: "fidelizacao",
    label: "Fidelização",
    icon: <IconGem />,
    pages: [
      { to: "/retention", label: "Retenção", hint: "cadeiras vazias e quem sumiu", icon: <IconPulse /> },
      { to: "/plans", label: "Planos", hint: "clube de assinatura", icon: <IconGem />, feature: "CLUB" },
      { to: "/packages", label: "Pacotes", hint: "cortes pagos adiantado", icon: <IconBox />, feature: "PACKAGES" },
      { to: "/coupons", label: "Cupons", hint: "descontos e fidelidade", icon: <IconTicket /> },
    ],
  },
  {
    id: "dinheiro",
    label: "Dinheiro",
    icon: <IconWallet />,
    pages: [
      { to: "/caixa", label: "Caixa", hint: "abrir, fechar e movimentos", icon: <IconCash /> },
      { to: "/commissions", label: "Comissões", hint: "quanto cada barbeiro fez", icon: <IconPercent />, end: true },
      { to: "/commissions/settlement", label: "Acerto", hint: "o que já foi pago à equipe", icon: <IconHandshake /> },
      { to: "/expenses", label: "Despesas e DRE", hint: "contas a pagar e o resultado", icon: <IconTrendDown /> },
      { to: "/products", label: "Produtos e estoque", hint: "o que se vende no balcão", icon: <IconBox /> },
    ],
  },
  {
    id: "configurar",
    label: "Configurar",
    icon: <IconSliders />,
    pages: [
      { to: "/services", label: "Serviços", hint: "preços e duração", icon: <IconTag /> },
      { to: "/professionals", label: "Profissionais", hint: "equipe, comissão e login", icon: <IconUser /> },
      { to: "/settings", label: "Configurações", hint: "bot, WhatsApp e site", icon: <IconSliders /> },
      { to: "/profile", label: "Meu perfil", hint: "nome e senha", icon: <IconUser /> },
    ],
  },
];

function showInMenu(p: NavPage, role: UserRole | undefined): boolean {
  return canAccess(p.to, role) && featureEnabled(p.feature) && (!p.menuRoles || (!!role && p.menuRoles.includes(role)));
}

/** Todas as páginas (para o Ctrl+K), filtradas só pelo papel — sem o filtro de menu. */
export function reachablePages(role: UserRole | undefined): NavPage[] {
  return [...QUICK, ...GROUPS.flatMap((g) => g.pages)].filter((p) => canAccess(p.to, role) && featureEnabled(p.feature));
}

/** Atalhos que o papel vê no menu. */
export function visibleQuick(role: UserRole | undefined): NavPage[] {
  return QUICK.filter((p) => showInMenu(p, role));
}

/** Grupos com as páginas que o papel vê no menu. Grupo sem página visível some inteiro. */
export function visibleGroups(role: UserRole | undefined): NavGroup[] {
  return GROUPS.map((g) => ({ ...g, pages: g.pages.filter((p) => showInMenu(p, role)) })).filter(
    (g) => g.pages.length > 0,
  );
}

/** A página `p` é a rota atual (ou um prefixo dela)? */
export function pathMatches(p: NavPage, pathname: string): boolean {
  return p.to === "/" ? pathname === "/" : pathname === p.to || pathname.startsWith(`${p.to}/`);
}

/** Grupo da rota atual (para abrir o bloco certo e acender o "Mais" do celular). */
export function groupOf(pathname: string): GroupId | null {
  let best: { id: GroupId; len: number } | null = null;
  for (const g of GROUPS) {
    for (const p of g.pages) {
      if (pathMatches(p, pathname) && (!best || p.to.length > best.len)) best = { id: g.id, len: p.to.length };
    }
  }
  return best?.id ?? null;
}

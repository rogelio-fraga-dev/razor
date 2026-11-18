// Arquitetura de informação do painel: 5 ÁREAS pensadas pelo que o barbeiro faz no dia, não
// pelas telas do sistema. Fonte única para o menu lateral, a navegação inferior do celular e a
// barra de comandos (Ctrl+K) — mudar aqui muda nos três.
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

export type AreaId = "hoje" | "agenda" | "clientes" | "dinheiro" | "configurar";

export interface NavPage {
  to: string;
  label: string;
  /** Uma linha em linguagem simples: aparece na folha da área (celular) e no Ctrl+K. */
  hint: string;
  icon: ReactNode;
  /** NavLink `end`: necessário quando a rota é prefixo de outra (/commissions × /settlement). */
  end?: boolean;
  /**
   * Restringe a exibição NO MENU além do `canAccess`. O "Meu dia" é aberto ao dono (que também
   * corta cabelo) pela barra de comandos, mas no menu só aparece para o barbeiro — no menu do
   * dono a área "Hoje" leva direto ao Painel, em 1 toque.
   */
  menuRoles?: UserRole[];
}

export interface NavArea {
  id: AreaId;
  label: string;
  icon: ReactNode;
  pages: NavPage[];
}

export const AREAS: NavArea[] = [
  {
    id: "hoje",
    label: "Hoje",
    icon: <IconSun />,
    pages: [
      { to: "/", label: "Painel", hint: "o dia da barbearia num olhar", icon: <IconGrid />, end: true },
      { to: "/meu-dia", label: "Meu dia", hint: "meus horários e quanto eu ganhei", icon: <IconSun />, menuRoles: ["BARBER"] },
    ],
  },
  {
    id: "agenda",
    label: "Agenda",
    icon: <IconCalendar />,
    pages: [{ to: "/agenda", label: "Agenda", hint: "horários por barbeiro", icon: <IconCalendar /> }],
  },
  {
    id: "clientes",
    label: "Clientes",
    icon: <IconUsers />,
    pages: [
      { to: "/customers", label: "Clientes", hint: "base, assinantes e histórico", icon: <IconUsers /> },
      { to: "/retention", label: "Retenção", hint: "cadeiras vazias e quem sumiu", icon: <IconPulse /> },
      { to: "/plans", label: "Planos", hint: "clube de assinatura", icon: <IconGem /> },
      { to: "/packages", label: "Pacotes", hint: "cortes pagos adiantado", icon: <IconBox /> },
      { to: "/coupons", label: "Cupons", hint: "descontos e fidelidade", icon: <IconTicket /> },
    ],
  },
  {
    id: "dinheiro",
    label: "Dinheiro",
    icon: <IconWallet />,
    pages: [
      { to: "/caixa", label: "Caixa", hint: "abrir, fechar e movimentos", icon: <IconCash /> },
      { to: "/comandas", label: "Comandas", hint: "a conta de cada atendimento", icon: <IconReceipt /> },
      { to: "/products", label: "Produtos e estoque", hint: "o que se vende no balcão", icon: <IconBox /> },
      { to: "/commissions", label: "Comissões", hint: "quanto cada barbeiro fez", icon: <IconPercent />, end: true },
      { to: "/commissions/settlement", label: "Acerto", hint: "o que já foi pago à equipe", icon: <IconHandshake /> },
      { to: "/expenses", label: "Despesas e DRE", hint: "contas a pagar e o resultado", icon: <IconTrendDown /> },
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

/** Todas as páginas (para o Ctrl+K), filtradas só pelo papel — sem o filtro de menu. */
export function reachablePages(role: UserRole | undefined): NavPage[] {
  return AREAS.flatMap((a) => a.pages).filter((p) => canAccess(p.to, role));
}

/** Áreas com as páginas que o papel vê no menu. Área sem página some inteira. */
export function visibleAreas(role: UserRole | undefined): NavArea[] {
  return AREAS.map((a) => ({
    ...a,
    pages: a.pages.filter((p) => canAccess(p.to, role) && (!p.menuRoles || (role && p.menuRoles.includes(role)))),
  })).filter((a) => a.pages.length > 0);
}

/** Área da rota atual (para acender o item certo da navegação inferior). */
export function areaOf(pathname: string): AreaId | null {
  let best: { id: AreaId; len: number } | null = null;
  for (const a of AREAS) {
    for (const p of a.pages) {
      const hit = p.to === "/" ? pathname === "/" : pathname === p.to || pathname.startsWith(`${p.to}/`);
      if (hit && (!best || p.to.length > best.len)) best = { id: a.id, len: p.to.length };
    }
  }
  return best?.id ?? null;
}

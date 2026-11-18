import type { UserRole } from "../api/types";

/**
 * Quem enxerga cada tela do painel.
 *
 * ⚠️ Isto é ESPELHO da tabela de autorização do backend (`SecurityConfig`), não a fonte da
 * verdade. Esconder um item de menu não protege nada — quem quiser é só digitar a URL, e a
 * API é que recusa. O papel disto aqui é outro: sem ele a recepção veria "Comissões" no menu,
 * clicaria e receberia um erro, o que parece defeito do sistema em vez de falta de permissão.
 *
 * Ao mexer nas regras do `SecurityConfig`, mexa aqui junto — as duas listas descrevem a mesma
 * decisão de produto e divergir faz uma delas mentir.
 */

const ALL: UserRole[] = ["OWNER", "RECEPTION", "BARBER"];
const COUNTER: UserRole[] = ["OWNER", "RECEPTION"];
const OWNER_ONLY: UserRole[] = ["OWNER"];

/** Papéis autorizados por rota do painel. Rota ausente = só o dono (mesmo fecho do backend). */
export const ROUTE_ROLES: Record<string, UserRole[]> = {
  "/": OWNER_ONLY, // o Painel é financeiro: faturamento, meta, ranking
  // "Meu dia" (V44): horários e comissão do PRÓPRIO profissional. A API escopa pelo login; a
  // recepção não corta cabelo, então não ganha a tela.
  "/meu-dia": ["OWNER", "BARBER"],
  "/agenda": ALL,
  "/profile": ALL,
  "/customers": COUNTER,
  "/comandas": COUNTER,
  "/caixa": COUNTER,
  "/services": COUNTER, // leitura do catálogo; a escrita é recusada pela API
  "/products": COUNTER,
  "/professionals": OWNER_ONLY,
  "/plans": OWNER_ONLY,
  "/coupons": OWNER_ONLY,
  "/packages": OWNER_ONLY, // vender pacote mexe no caixa e o catálogo define preço
  "/commissions": OWNER_ONLY,
  "/commissions/settlement": OWNER_ONLY,
  "/expenses": OWNER_ONLY, // contas a pagar e DRE: o resultado do negócio é do dono
  "/retention": OWNER_ONLY, // ocupação e retenção: decisão de negócio, não operação
  "/settings": OWNER_ONLY,
};

export function canAccess(path: string, role: UserRole | undefined): boolean {
  if (!role) return false;
  return (ROUTE_ROLES[path] ?? OWNER_ONLY).includes(role);
}

/**
 * Para onde mandar o usuário ao entrar (ou ao tentar uma rota que não é dele).
 * O dono cai no Painel; quem não vê financeiro cai na primeira tela que de fato usa.
 */
export function homeFor(role: UserRole | undefined): string {
  if (role === "RECEPTION") return "/comandas";
  if (role === "BARBER") return "/meu-dia";
  return "/";
}

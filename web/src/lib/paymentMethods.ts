import type { CashPaymentMethod } from "../api/types";

/** Rótulo de cada forma de pagamento do caixa. Uma fonte só para comanda, caixa e balcão. */
export const METHOD_LABEL: Record<CashPaymentMethod, string> = {
  DINHEIRO: "Dinheiro",
  PIX: "PIX",
  DEBITO: "Débito",
  CREDITO: "Crédito",
  OUTRO: "Outro",
};

/** Ordem de exibição nos selects (dinheiro primeiro: é o que mexe na gaveta). */
export const PAYMENT_METHODS: { value: CashPaymentMethod; label: string }[] = (
  Object.keys(METHOD_LABEL) as CashPaymentMethod[]
).map((value) => ({ value, label: METHOD_LABEL[value] }));

/** Formas que aceitam taxa (V35) — dinheiro entra inteiro na gaveta. */
export const FEE_METHODS: CashPaymentMethod[] = ["PIX", "DEBITO", "CREDITO", "OUTRO"];

/** Arredonda ao centavo sem herdar o lixo do ponto flutuante (0,1 + 0,2). */
export function cents(value: number): number {
  return Math.round(value * 100) / 100;
}

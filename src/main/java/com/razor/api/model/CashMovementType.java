package com.razor.api.model;

/**
 * Tipo de movimento do caixa.
 *
 * <ul>
 *   <li>{@link #VENDA} — entrada por atendimento/produto; carrega a forma de pagamento e só
 *       afeta a gaveta quando for {@link CashPaymentMethod#DINHEIRO}.</li>
 *   <li>{@link #SANGRIA} — retirada de dinheiro da gaveta (troco excedente, depósito).</li>
 *   <li>{@link #SUPRIMENTO} — reforço de dinheiro na gaveta (troco).</li>
 * </ul>
 */
public enum CashMovementType {
    VENDA,
    SANGRIA,
    SUPRIMENTO
}

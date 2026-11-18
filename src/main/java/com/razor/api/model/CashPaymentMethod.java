package com.razor.api.model;

/**
 * Forma de pagamento de uma venda no caixa. Só o {@link #DINHEIRO} entra na gaveta física —
 * os demais entram na conta, mas não mudam o valor esperado no fechamento do caixa.
 *
 * <p>{@link #OUTRO} (V35) cobre o que não é nenhum dos quatro: vale, transferência, permuta.
 */
public enum CashPaymentMethod {
    DINHEIRO,
    PIX,
    DEBITO,
    CREDITO,
    OUTRO;

    /** True quando o pagamento entra na gaveta física (afeta a conferência de fechamento). */
    public boolean affectsDrawer() {
        return this == DINHEIRO;
    }

    /** Dinheiro entra inteiro na gaveta; os demais podem ter taxa configurada (V35). */
    public boolean acceptsFee() {
        return this != DINHEIRO;
    }
}

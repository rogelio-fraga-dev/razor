package com.razor.api.model;

/**
 * Forma de pagamento de uma venda no caixa. Só o {@link #DINHEIRO} entra na gaveta física —
 * os demais entram na conta, mas não mudam o valor esperado no fechamento do caixa.
 */
public enum CashPaymentMethod {
    DINHEIRO,
    PIX,
    DEBITO,
    CREDITO;

    /** True quando o pagamento entra na gaveta física (afeta a conferência de fechamento). */
    public boolean affectsDrawer() {
        return this == DINHEIRO;
    }
}

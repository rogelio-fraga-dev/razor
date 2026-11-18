package com.razor.api.model;

/**
 * Sinal exigido de cliente com faltas repetidas (V42). {@code null} na coluna = nao exigido.
 */
public enum DepositStatus {
    /** O bot mandou o PIX do sinal; o dono ainda nao confirmou o recebimento. */
    PENDING,
    /** Recebimento confirmado no painel. */
    PAID
}

package com.razor.api.model;

/**
 * Resposta do cliente a pergunta de confirmacao da vespera (V42). {@code null} na coluna = nao foi
 * perguntado.
 */
public enum ConfirmationStatus {
    /** Pergunta enviada, sem resposta ainda. */
    PENDING,
    /** O cliente respondeu que vem. */
    CONFIRMED,
    /** O cliente respondeu que nao vem (o horario e liberado). */
    DECLINED,
    /** Passou do prazo sem resposta; o dono foi avisado. */
    NO_RESPONSE
}

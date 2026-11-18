package com.razor.api.model;

/**
 * Situacao do cliente na base, <b>calculada</b> do historico de visitas (nunca gravada — ficaria
 * velha no dia seguinte). A regra e a do relatorio de retencao, centralizada no
 * {@code CustomerStatusService}.
 */
public enum CustomerStatus {
    /** Cadastrado ha pouco e sem segunda visita ainda. */
    NOVO,
    /** Voltando dentro do proprio ritmo. */
    ATIVO,
    /** Passou do intervalo esperado: vale um lembrete antes que suma. */
    EM_RISCO,
    /** Passou do dobro do intervalo esperado (ou cadastro antigo que nunca virou visita). */
    SUMIDO
}

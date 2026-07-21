package com.razor.api.model;

/**
 * Situacao de pagamento de um agendamento.
 *
 * <p>Substitui as strings soltas ("PENDENTE"/"PIX"/"PRESENCIAL") por um tipo forte.
 * Persistido via {@code @Enumerated(EnumType.STRING)}, entao os nomes abaixo casam
 * exatamente com os valores ja existentes na coluna {@code payment_status} — sem
 * necessidade de migracao de dados.
 */
public enum PaymentStatus {
    PENDENTE,
    PIX,
    PRESENCIAL;

    /**
     * Converte um texto livre para o enum, de forma tolerante.
     *
     * @param value texto (pode ter espacos/caixa variada); {@code null}/desconhecido vira {@link #PENDENTE}.
     */
    public static PaymentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDENTE;
        }
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDENTE;
        }
    }
}

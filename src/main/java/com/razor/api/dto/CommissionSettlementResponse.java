package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A visao de acerto de um periodo: o que cada barbeiro apurou, o que ja recebeu e o saldo.
 * E a tela que o dono olha antes de pagar — sem ela, pagar a mesma quinzena duas vezes e facil.
 *
 * <p>{@code pago} soma apenas os acertos <b>contidos</b> no intervalo consultado.
 */
public record CommissionSettlementResponse(
        LocalDate from,
        LocalDate to,
        List<Item> items,
        BigDecimal totalComissao,
        BigDecimal totalPago,
        BigDecimal totalAReceber) {

    public record Item(
            UUID professionalId,
            String professionalName,
            int atendimentos,
            BigDecimal faturamento,
            int produtos,
            BigDecimal faturamentoProdutos,
            BigDecimal comissao,
            BigDecimal pago,
            BigDecimal aReceber) {
    }
}

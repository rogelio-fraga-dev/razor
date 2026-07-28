package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A visao de acerto de um periodo: o que cada barbeiro apurou, o que ja recebeu e o saldo.
 * E a tela que o dono olha antes de pagar — sem ela, pagar a mesma quinzena duas vezes e facil.
 *
 * <p>{@code pago} soma apenas os acertos <b>contidos</b> no intervalo consultado; {@code vales}
 * soma os adiantamentos com data dentro dele (V26).
 *
 * <p>{@code aReceber} = comissao − pago − vales, e pode ficar <b>negativo</b>: quando o vale passa
 * da comissao apurada, o barbeiro deve a barbearia, e esconder isso zerando o numero mentiria.
 */
public record CommissionSettlementResponse(
        LocalDate from,
        LocalDate to,
        List<Item> items,
        BigDecimal totalComissao,
        BigDecimal totalPago,
        BigDecimal totalVales,
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
            BigDecimal vales,
            BigDecimal aReceber) {
    }
}

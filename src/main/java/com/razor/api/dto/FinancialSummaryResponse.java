package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo financeiro estruturado de um periodo (para o painel web). Espelha o que o resumo de
 * WhatsApp mostra em texto, mas em dados: volume por categoria, fluxo por meio de pagamento,
 * faturamento e ticket medio.
 */
public record FinancialSummaryResponse(
        LocalDate from,
        LocalDate to,
        int totalAppointments,
        CategoryBreakdown categories,
        PaymentBreakdown payments,
        BigDecimal totalRevenue,
        BigDecimal averageTicket) {

    public record CategoryBreakdown(int combo, int barba, int simples) {
    }

    public record PaymentBreakdown(Bucket pix, Bucket presencial, Bucket pendente) {
    }

    public record Bucket(int count, BigDecimal value) {
    }
}

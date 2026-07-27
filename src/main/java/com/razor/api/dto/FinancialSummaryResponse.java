package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo financeiro estruturado de um periodo (para o painel web). Espelha o que o resumo de
 * WhatsApp mostra em texto, mas em dados: volume por categoria, fluxo por meio de pagamento,
 * faturamento e ticket medio.
 *
 * <p><b>Servicos e produtos vem separados de proposito.</b> {@code servicesRevenue} e o que veio
 * dos agendamentos; {@code productsRevenue} e o que veio das vendas de produto (balcao V21 e
 * comanda V22, que gravam na mesma tabela e por isso nao se somam duas vezes).
 * {@code totalRevenue} e a soma dos dois — o faturamento da barbearia no periodo.
 *
 * <p>{@code averageTicket} continua sendo <b>servicos / atendimentos</b>: e o valor medio do
 * atendimento. Misturar produto ali inflaria o ticket com venda de balcao que nem teve
 * atendimento junto.
 *
 * <p>{@code payments} descreve so os <b>agendamentos</b> (PIX/presencial/pendente). A forma de
 * pagamento das vendas de produto vive no caixa, com outro vocabulario
 * (dinheiro/PIX/debito/credito), e por isso nao e misturada aqui.
 */
public record FinancialSummaryResponse(
        LocalDate from,
        LocalDate to,
        int totalAppointments,
        CategoryBreakdown categories,
        PaymentBreakdown payments,
        BigDecimal servicesRevenue,
        int productsSold,
        BigDecimal productsRevenue,
        BigDecimal totalRevenue,
        BigDecimal averageTicket) {

    public record CategoryBreakdown(int combo, int barba, int simples) {
    }

    public record PaymentBreakdown(Bucket pix, Bucket presencial, Bucket pendente) {
    }

    public record Bucket(int count, BigDecimal value) {
    }
}

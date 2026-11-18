package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
 * (dinheiro/PIX/debito/credito), e por isso nao e misturada aqui — ela vem em {@code receipts}
 * (V35), lida dos movimentos do caixa, com split e taxas.
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
        BigDecimal averageTicket,
        Receipts receipts) {

    /** Construtor anterior a V35 (sem recebimentos por forma de pagamento). */
    public FinancialSummaryResponse(LocalDate from, LocalDate to, int totalAppointments,
                                    CategoryBreakdown categories, PaymentBreakdown payments,
                                    BigDecimal servicesRevenue, int productsSold, BigDecimal productsRevenue,
                                    BigDecimal totalRevenue, BigDecimal averageTicket) {
        this(from, to, totalAppointments, categories, payments, servicesRevenue, productsSold,
                productsRevenue, totalRevenue, averageTicket, Receipts.EMPTY);
    }

    public record CategoryBreakdown(int combo, int barba, int simples) {
    }

    /**
     * O que <b>entrou no caixa</b> no periodo, por forma de pagamento (V35) — lido dos movimentos
     * de VENDA, entao comanda paga em split aparece certinha: R$ 50 no dinheiro e R$ 30 no
     * credito contam em cada linha. {@code fees} e a taxa da maquininha congelada em cada venda;
     * {@code net} = {@code gross - fees}.
     *
     * <p>Nao e o mesmo numero que {@code totalRevenue}: faturamento conta agendamentos pelo
     * preco de catalogo (inclusive os ainda nao pagos); recebimentos contam so o dinheiro que
     * passou pelo caixa.
     */
    public record Receipts(List<MethodLine> methods, BigDecimal gross, BigDecimal fees, BigDecimal net) {
        public static final Receipts EMPTY =
                new Receipts(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Uma forma de pagamento: quantas partes, quanto bruto, quanto de taxa e o liquido. */
    public record MethodLine(CashPaymentMethod method, int count, BigDecimal gross, BigDecimal fees,
                             BigDecimal net) {
    }

    public record PaymentBreakdown(Bucket pix, Bucket presencial, Bucket pendente) {
    }

    public record Bucket(int count, BigDecimal value) {
    }
}

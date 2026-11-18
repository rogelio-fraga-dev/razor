package com.razor.api.dto;

import com.razor.api.model.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DRE do periodo: o que entrou, o que saiu e o que sobrou.
 *
 * <p><b>Despesa entra por competencia ({@code dueDate}), nao por desembolso.</b> O aluguel que
 * vence dia 5 pertence ao mes do vencimento mesmo que tenha sido pago dia 8 — senao um mes em
 * que o dono atrasou dois boletos apareceria lucrativo, e o mes seguinte, no prejuizo, sem que
 * nada tenha mudado na operacao. Quem precisa da visao de desembolso e a conferencia da gaveta,
 * e essa vive no caixa.
 *
 * <p>Por isso {@code expensesPaid} e {@code expensesUnpaid} vem separados: somam o mesmo
 * {@code totalExpenses}, mas dizem quanto daquilo ja saiu de fato.
 *
 * <p><b>Taxas de pagamento (V35)</b> sao deducao da receita: {@code result = netRevenue -
 * totalExpenses}, com {@code netRevenue = totalRevenue - paymentFees}. Sem taxa cadastrada,
 * {@code paymentFees} e zero e nada muda.
 *
 * <p>{@code marginPercent} e <b>nulo</b> quando nao houve receita no periodo. Dividir por zero
 * daria infinito e mostrar {@code 0%} seria pior: sugere margem nula quando o certo e "nao ha o
 * que medir".
 */
public record DreResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal servicesRevenue,
        BigDecimal productsRevenue,
        BigDecimal totalRevenue,
        /** Taxas dos meios de pagamento no periodo (V35) — deducao da receita. */
        BigDecimal paymentFees,
        /** {@code totalRevenue - paymentFees}: o que de fato fica com a barbearia. */
        BigDecimal netRevenue,
        List<ExpenseLine> expenses,
        BigDecimal totalExpenses,
        BigDecimal expensesPaid,
        BigDecimal expensesUnpaid,
        BigDecimal overdueAmount,
        BigDecimal result,
        BigDecimal marginPercent) {

    /**
     * Uma linha de despesa do DRE.
     *
     * <p>{@code shareOfRevenue} responde a pergunta que o dono realmente faz — "o aluguel come
     * quantos por cento do que eu faturo?". Nulo sem receita, pelo mesmo motivo da margem.
     */
    public record ExpenseLine(
            ExpenseCategory category,
            String label,
            BigDecimal amount,
            BigDecimal shareOfRevenue) {
    }
}

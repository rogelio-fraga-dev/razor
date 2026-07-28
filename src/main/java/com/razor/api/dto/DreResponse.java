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

package com.razor.api.dto;

import com.razor.api.model.Expense;
import com.razor.api.model.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conta a pagar como o painel a exibe.
 *
 * <p>{@code paid} e {@code overdue} vem calculados do servidor de proposito: sao a mesma
 * pergunta que o DRE responde, e deixar o front decidir "esta vencida?" abriria espaco para as
 * duas telas discordarem por causa do fuso do navegador.
 *
 * <p>{@code fromCashRegister} conta se o pagamento saiu da gaveta — e o que explica ao dono por
 * que aquela conta nao pode mais ser editada nem apagada.
 */
public record ExpenseResponse(
        UUID id,
        String description,
        ExpenseCategory category,
        String categoryLabel,
        BigDecimal amount,
        LocalDate dueDate,
        LocalDate paidAt,
        boolean paid,
        boolean overdue,
        boolean recurring,
        boolean fromCashRegister,
        String notes) {

    public static ExpenseResponse from(Expense e, LocalDate today) {
        return new ExpenseResponse(
                e.getId(),
                e.getDescription(),
                e.getCategory(),
                e.getCategory().label(),
                e.getAmount(),
                e.getDueDate(),
                e.getPaidAt(),
                e.isPaid(),
                e.isOverdue(today),
                e.isRecurring(),
                e.getCashMovementId() != null,
                e.getNotes());
    }
}

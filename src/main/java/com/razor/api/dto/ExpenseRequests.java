package com.razor.api.dto;

import com.razor.api.model.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Payloads da tela de Despesas (contas a pagar, V31). */
public final class ExpenseRequests {

    private ExpenseRequests() {
    }

    /**
     * Cadastro de conta.
     *
     * <p>{@code dueDate} nula = hoje. {@code paidAt} preenchida ja lanca a conta como paga (o
     * caso de quem cadastra o boleto depois de pagar); nula deixa em aberto.
     * {@code fromCashRegister} so tem efeito com {@code paidAt}: conta em aberto nao tira
     * dinheiro de gaveta nenhuma.
     */
    public record Create(
            @NotBlank(message = "Informe a descrição da despesa.")
                    @Size(max = 255, message = "A descrição é longa demais.") String description,
            @NotNull(message = "Informe a categoria.") ExpenseCategory category,
            @NotNull(message = "Informe o valor.")
                    @Positive(message = "O valor da despesa deve ser maior que zero.") BigDecimal amount,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidAt,
            Boolean fromCashRegister,
            Boolean recurring,
            @Size(max = 255, message = "A observação é longa demais.") String notes) {
    }

    /** Edicao dos dados da conta. Quitar e comando proprio — ver {@link Pay}. */
    public record Update(
            @NotBlank(message = "Informe a descrição da despesa.")
                    @Size(max = 255, message = "A descrição é longa demais.") String description,
            @NotNull(message = "Informe a categoria.") ExpenseCategory category,
            @NotNull(message = "Informe o valor.")
                    @Positive(message = "O valor da despesa deve ser maior que zero.") BigDecimal amount,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            Boolean recurring,
            @Size(max = 255, message = "A observação é longa demais.") String notes) {
    }

    /**
     * Quitacao. {@code paidAt} nula = hoje; {@code fromCashRegister} exige caixa aberto e gera a
     * sangria (pagamento em especie).
     */
    public record Pay(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidAt,
            Boolean fromCashRegister) {
    }
}

package com.razor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Registro de um acerto de comissao.
 *
 * <p>{@code fromCashRegister} decide se o dinheiro sai da gaveta: {@code true} exige caixa aberto
 * e gera sangria; {@code false} (padrao) e o pagamento por fora — PIX, transferencia — que nao
 * pode mexer na conferencia do caixa.
 */
public record CommissionPaymentRequest(
        @NotNull(message = "Informe o profissional.") UUID professionalId,
        @NotNull(message = "Informe o inicio do periodo.")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
        @NotNull(message = "Informe o fim do periodo.")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
        @NotNull(message = "Informe o valor do acerto.")
                @Positive(message = "O valor do acerto deve ser maior que zero.") BigDecimal amount,
        Boolean fromCashRegister,
        String notes) {
}

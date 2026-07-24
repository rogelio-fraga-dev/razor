package com.razor.api.dto;

import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Um movimento a registrar no caixa aberto. {@code paymentMethod} só se aplica a
 * {@link CashMovementType#VENDA} (validado no serviço).
 */
public record CashMovementRequest(
        @NotNull(message = "Informe o tipo do movimento.")
        CashMovementType type,
        CashPaymentMethod paymentMethod,
        @NotNull(message = "Informe o valor.")
        @Positive(message = "O valor deve ser maior que zero.")
        BigDecimal amount,
        String description) {
}

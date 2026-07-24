package com.razor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Fechamento de caixa: o valor efetivamente contado na gaveta + observações opcionais. */
public record CloseCashRequest(
        @NotNull(message = "Informe o valor contado na gaveta.")
        @PositiveOrZero(message = "O valor contado não pode ser negativo.")
        BigDecimal counted,
        String notes) {
}

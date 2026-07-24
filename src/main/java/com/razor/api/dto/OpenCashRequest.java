package com.razor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Abertura de caixa: o fundo de troco inicial (pode ser zero). */
public record OpenCashRequest(
        @NotNull(message = "Informe o fundo de troco (pode ser 0).")
        @PositiveOrZero(message = "O fundo de troco não pode ser negativo.")
        BigDecimal openingAmount) {
}

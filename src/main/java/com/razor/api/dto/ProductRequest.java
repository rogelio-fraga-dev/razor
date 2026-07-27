package com.razor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Produto do balcao vindo do painel. {@code active} nulo no PUT significa "manter ativo" —
 * a desativacao tem endpoint proprio, para nao virar efeito colateral de uma edicao de preco.
 */
public record ProductRequest(
        @NotBlank(message = "Informe o nome do produto.") String name,
        @NotNull(message = "Informe o preco do produto.")
                @Positive(message = "O preco do produto deve ser maior que zero.")
                BigDecimal price,
        Boolean active) {}

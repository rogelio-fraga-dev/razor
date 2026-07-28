package com.razor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Payloads de estoque (V24). */
public final class StockRequests {

    private StockRequests() {}

    /** Reposicao ou perda: quantidade sempre positiva; o sentido vem da rota. */
    public record Movement(
            @NotNull(message = "Informe a quantidade.")
                    @Positive(message = "A quantidade deve ser maior que zero.")
                    Integer quantity,
            String note) {}

    /** Contagem fisica: o saldo passa a ser exatamente esta quantidade. */
    public record Adjustment(
            @NotNull(message = "Informe a quantidade contada.")
                    @PositiveOrZero(message = "A contagem nao pode ser negativa.")
                    Integer countedQuantity,
            String note) {}

    /** Liga/desliga o controle e define o nivel de reposicao. */
    public record Config(
            @NotNull(message = "Informe se o estoque deste produto e controlado.") Boolean trackStock,
            @PositiveOrZero(message = "O estoque minimo nao pode ser negativo.") Integer minStock) {}
}

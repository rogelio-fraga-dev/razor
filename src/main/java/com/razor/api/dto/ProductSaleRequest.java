package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Venda de balcao vinda do painel. O preco nao vem do cliente de proposito: quem manda e o
 * catalogo no momento da venda, senao daria para forjar o valor cobrado.
 *
 * <p>{@code professionalId} e opcional — venda sem barbeiro nao gera comissao.
 */
public record ProductSaleRequest(
        @NotNull(message = "Informe o produto.") UUID productId,
        @NotNull(message = "Informe a quantidade.")
                @Positive(message = "A quantidade deve ser maior que zero.")
                Integer quantity,
        UUID professionalId,
        @NotNull(message = "Escolha a forma de pagamento da venda.") CashPaymentMethod paymentMethod) {}

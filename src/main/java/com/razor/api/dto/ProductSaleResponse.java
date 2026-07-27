package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.ProductSale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Venda de balcao como o painel consome. Nome e preco sao os congelados na venda. */
public record ProductSaleResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal total,
        UUID professionalId,
        String professionalName,
        CashPaymentMethod paymentMethod,
        LocalDateTime soldAt) {

    public static ProductSaleResponse from(ProductSale s, String professionalName) {
        return new ProductSaleResponse(
                s.getId(), s.getProductId(), s.getProductName(), s.getUnitPrice(),
                s.getQuantity(), s.getTotal(), s.getProfessionalId(), professionalName,
                s.getPaymentMethod(), s.getSoldAt());
    }
}

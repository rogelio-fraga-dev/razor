package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uma mudanca no estoque de um produto (V24). Mapeia {@code stock_movements}.
 *
 * <p>O saldo vive em {@link Product#getStockQuantity()}, mas toda mudanca passa por aqui: sem
 * historico, "meu estoque esta errado" e impossivel de investigar. {@code resultingStock} guarda
 * o saldo apos o movimento, entao o extrato fecha sem reprocessar a tabela inteira.
 */
@Entity
@Table(name = "stock_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockMovementType type;

    /** Sempre positivo; o sinal vem do tipo. */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "resulting_stock", nullable = false)
    private Integer resultingStock;

    /** Venda que originou a saida. Nulo em reposicao, perda e ajuste. */
    @Column(name = "product_sale_id")
    private UUID productSaleId;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

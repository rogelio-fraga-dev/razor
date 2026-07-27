package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um item de uma {@link Comanda} (V22): um servico ou um produto. Mapeia {@code comanda_items}.
 *
 * <p>Descricao e preco sao <b>copias</b> do catalogo no momento em que o item entrou na conta.
 * Reajuste de preco depois nao reescreve comanda ja aberta nem fechada.
 */
@Entity
@Table(name = "comanda_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComandaItem {

    public enum Kind {
        SERVICO,
        PRODUTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "comanda_id", nullable = false)
    private UUID comandaId;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    /** {@code services.id} ou {@code products.id} — pode ficar nulo se o item sair do catalogo. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(nullable = false)
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

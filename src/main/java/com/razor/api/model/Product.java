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
 * Produto vendido no balcao (pomada, bebida, cera...). Mapeia {@code products} (V20).
 *
 * <p>Diferente de {@link ServiceOffering}, produto nao tem duracao — nao ocupa cadeira nem
 * entra na agenda. Base da venda de balcao e da comanda por atendimento.
 */
@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    // --- Estoque (V24). Opt-in: o produto so e controlado se o dono ligar. ---

    /** Controla estoque deste produto? Desligado, a venda nunca e bloqueada por saldo. */
    @Builder.Default
    @Column(name = "track_stock", nullable = false)
    private boolean trackStock = false;

    /** Saldo atual. So tem significado com {@link #trackStock} ligado. */
    @Builder.Default
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    /** Abaixo disto o painel avisa que e hora de repor. 0 = sem alerta. */
    @Builder.Default
    @Column(name = "min_stock", nullable = false)
    private Integer minStock = 0;

    /** True quando o produto e controlado e o saldo caiu no nivel de reposicao. */
    public boolean isLowStock() {
        return trackStock && minStock > 0 && stockQuantity <= minStock;
    }

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

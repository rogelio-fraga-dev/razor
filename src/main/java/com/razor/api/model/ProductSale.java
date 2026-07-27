package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uma venda de balcao (V21): produto vendido junto do corte. Mapeia {@code product_sales}.
 *
 * <p><b>Nome e preco sao copias</b> do momento da venda, nao referencias vivas ao catalogo. Se o
 * dono reajustar o preco amanha, o relatorio de hoje tem que continuar mostrando o que foi
 * realmente cobrado — relatorio de dinheiro nao muda retroativamente.
 */
@Entity
@Table(name = "product_sales")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Opcional: sem barbeiro atribuido, a venda nao gera comissao. */
    @Column(name = "professional_id")
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 12)
    private CashPaymentMethod paymentMethod;

    /** Movimento de caixa criado por esta venda — amarra a venda ao fechamento da gaveta. */
    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

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
 * Pacote pre-pago do catalogo (V34) — ex.: "10 cortes por R$ 300". Mapeia {@code service_packages}.
 *
 * <p>Diferente do {@link SubscriptionPlan} (clube, recorrente): o pacote e pago de uma vez e
 * vira um credito de usos para o cliente ({@link CustomerPackage}).
 */
@Entity
@Table(name = "service_packages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Servico coberto. Nulo = qualquer servico do catalogo. */
    @Column(name = "service_id")
    private UUID serviceId;

    /** Quantos usos o pacote da. */
    @Column(nullable = false)
    private Integer quantity;

    /** Preco do pacote inteiro. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Prazo para usar, em dias contados da venda. */
    @Column(name = "validity_days", nullable = false)
    private Integer validityDays;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

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

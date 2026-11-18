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
 * Pacote vendido a um cliente (V34). Mapeia {@code customer_packages}.
 *
 * <p>Nome, servico, preco e validade sao <b>copias</b> do catalogo no momento da venda: reajustar
 * o pacote depois nao pode mudar o que o cliente ja pagou.
 */
@Entity
@Table(name = "customer_packages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Pacote do catalogo de origem — pode ficar nulo se o pacote sair do catalogo. */
    @Column(name = "package_id")
    private UUID packageId;

    @Column(name = "package_name", nullable = false, length = 120)
    private String packageName;

    /** Servico coberto (copia). Nulo = qualquer servico. */
    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "total_uses", nullable = false)
    private Integer totalUses;

    @Column(name = "remaining_uses", nullable = false)
    private Integer remainingUses;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Preco / usos, congelado na venda: e a base da comissao do barbeiro em cada uso. */
    @Column(name = "unit_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 12)
    private CashPaymentMethod paymentMethod;

    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    /** Ultimo dia em que o pacote vale (inclusive). */
    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

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

    /** Vencido = passou do ultimo dia de validade. */
    public boolean isExpiredOn(LocalDate date) {
        return expiresAt != null && date.isAfter(expiresAt);
    }

    /** Usavel numa data: com saldo e dentro da validade. */
    public boolean isUsableOn(LocalDate date) {
        return remainingUses != null && remainingUses > 0 && !isExpiredOn(date);
    }

    /** Cobre o servico? Pacote sem servico definido cobre qualquer um. */
    public boolean covers(UUID serviceOfferingId) {
        return serviceId == null || serviceId.equals(serviceOfferingId);
    }
}

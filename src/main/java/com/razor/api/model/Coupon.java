package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cupom de desconto (V29). Mapeia {@code coupons}.
 *
 * <p>Nasce de duas origens: o dono cria uma campanha ({@link Origin#MANUAL}) ou o cartao
 * fidelidade emite ao cliente que completou os pontos ({@link Origin#FIDELIDADE}).
 */
@Entity
@Table(name = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    public enum DiscountType {
        /** Percentual sobre o total da conta. */
        PERCENT,
        /** Valor fixo em reais. */
        FIXED
    }

    public enum Origin {
        MANUAL,
        FIDELIDADE,
        /** Presente da campanha automatica de aniversario (V47). */
        ANIVERSARIO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Origin origin = Origin.MANUAL;

    /** Cupom pessoal: so este telefone usa. Nulo = campanha aberta. */
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** Usos totais permitidos. Nulo = ilimitado. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "min_amount", precision = 12, scale = 2)
    private BigDecimal minAmount;

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

    /** Quanto este cupom tira de uma conta — nunca mais do que o proprio total. */
    public BigDecimal discountFor(BigDecimal total) {
        if (total == null || total.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal desconto = discountType == DiscountType.PERCENT
                ? total.multiply(discountValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : discountValue;
        return desconto.min(total).setScale(2, RoundingMode.HALF_UP);
    }

    /** Ainda ha uso disponivel? Sem limite cadastrado, sempre. */
    public boolean hasUsesLeft() {
        return maxUses == null || (usedCount == null ? 0 : usedCount) < maxUses;
    }

    /** Esta dentro da janela de validade na data informada? */
    public boolean isValidOn(LocalDate date) {
        if (validFrom != null && date.isBefore(validFrom)) return false;
        return validUntil == null || !date.isAfter(validUntil);
    }
}

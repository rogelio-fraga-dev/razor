package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um beneficio que o plano da ao assinante (V27). Mapeia {@code plan_benefits}.
 *
 * <p>Modelado a partir do app do cliente: um plano da "100% OFF em Corte, 999 por mes, segunda a
 * quarta" e "10% OFF em Cosmeticos". O mesmo registro cobre os dois casos — com
 * {@link #targetId} preenchido vale para um item; nulo, vale para todos do {@link #targetType}.
 */
@Entity
@Table(name = "plan_benefits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanBenefit {

    public enum TargetType {
        SERVICO,
        PRODUTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    /** Item especifico; nulo = todos do {@link #targetType} ("Cosmeticos: 10% OFF"). */
    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Vezes por mes que o beneficio vale. Nulo = ilimitado. */
    @Column(name = "monthly_quota")
    private Integer monthlyQuota;

    /** CSV ISO dos dias em que vale (1=segunda ... 7=domingo). Nulo = todos os dias. */
    @Column(length = 20)
    private String weekdays;

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

    /** O beneficio vale neste dia? Sem restricao cadastrada, vale sempre. */
    public boolean appliesOn(DayOfWeek day) {
        if (weekdays == null || weekdays.isBlank()) return true;
        return Arrays.stream(weekdays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(day.getValue())));
    }

    /** True quando o beneficio zera o preco (o "GRATIS" da tela do app). */
    public boolean isFree() {
        return discountPercent != null && discountPercent.compareTo(new BigDecimal("100")) >= 0;
    }
}

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
 * Vale/adiantamento dado ao barbeiro (V26), descontado da comissao no acerto do periodo.
 * Mapeia {@code professional_advances}.
 *
 * <p>Nao tem estado "quitado": o vale pertence a uma {@link #advanceDate} e e descontado na
 * apuracao que contem essa data. Sem maquina de estados, nao ha o que dessincronizar.
 */
@Entity
@Table(name = "professional_advances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate;

    @Column(length = 255)
    private String notes;

    /** Sangria gerada quando o vale sai da gaveta. Nulo = adiantado por fora do caixa. */
    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

    @Column(name = "granted_by_name")
    private String grantedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

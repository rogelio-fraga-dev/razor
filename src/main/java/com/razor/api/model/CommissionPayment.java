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
 * Um acerto de comissao com o barbeiro (V25). Mapeia {@code commission_payments}.
 *
 * <p>Guarda o <b>periodo</b> a que se refere e o <b>valor copiado</b> no momento do pagamento: se
 * um atendimento antigo for corrigido depois, o recibo do que foi efetivamente pago nao muda.
 */
@Entity
@Table(name = "commission_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String notes;

    /** Sangria gerada quando o pagamento sai da gaveta. Nulo = pago por fora do caixa. */
    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

    @Column(name = "paid_by_name")
    private String paidByName;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

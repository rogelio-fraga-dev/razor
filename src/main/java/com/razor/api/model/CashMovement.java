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
 * Um movimento dentro de uma sessão de caixa (V18): venda, sangria ou suprimento. O
 * {@code amount} é sempre positivo (o sinal vem do {@link CashMovementType}). Mapeia
 * {@code cash_movements}.
 */
@Entity
@Table(name = "cash_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private CashMovementType type;

    /** Só preenchido em {@link CashMovementType#VENDA}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 12)
    private CashPaymentMethod paymentMethod;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

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
 * Uma sessão de caixa (V18): aberta com um fundo de troco, recebe movimentos ao longo do dia e
 * é fechada com a conferência da gaveta. No máximo uma {@code OPEN} por barbearia (índice único
 * parcial na V18). Mapeia {@code cash_register_sessions}.
 */
@Entity
@Table(name = "cash_register_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashRegisterSession {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "opened_by_user_id")
    private UUID openedByUserId;

    @Column(name = "opened_by_name")
    private String openedByName;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "opening_amount", nullable = false)
    private BigDecimal openingAmount;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

    @Column(name = "closed_by_name")
    private String closedByName;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Valor efetivamente contado na gaveta no fechamento. */
    @Column(name = "closing_counted")
    private BigDecimal closingCounted;

    /** Gaveta esperada, calculada dos movimentos no fechamento. */
    @Column(name = "closing_expected")
    private BigDecimal closingExpected;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

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

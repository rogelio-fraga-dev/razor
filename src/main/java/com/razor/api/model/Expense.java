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
 * Conta a pagar da barbearia (V31). Mapeia {@code expenses}.
 *
 * <p>Nao tem campo de status: {@link #isPaid()} deriva de {@link #paidAt}. Estado paralelo ao
 * fato e mais uma coisa para dessincronizar — mesmo criterio do vale ({@link ProfessionalAdvance}).
 *
 * <p>{@link #dueDate} e {@link #paidAt} sao datas separadas de proposito: a primeira e a
 * competencia (a qual mes a despesa pertence, e o que o DRE usa), a segunda e o desembolso (o
 * que a conferencia do caixa ve). O aluguel que vence dia 5 e e pago dia 8 pertence ao mes do
 * vencimento, mas sai da gaveta no dia do pagamento.
 */
@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Competencia: a qual periodo a despesa pertence. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Desembolso. Nulo = ainda em aberto. */
    @Column(name = "paid_at")
    private LocalDate paidAt;

    /** "Isso se repete todo mes" — marcador, nao gerador automatico. */
    @Column(nullable = false)
    private boolean recurring;

    @Column(length = 255)
    private String notes;

    /** Sangria gerada quando a despesa sai da gaveta. Nulo = paga por fora do caixa. */
    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Paga = tem data de desembolso. Nao existe terceiro estado. */
    public boolean isPaid() {
        return paidAt != null;
    }

    /** Vencida e ainda em aberto na data de referencia — o que cobra atencao do dono. */
    public boolean isOverdue(LocalDate reference) {
        return !isPaid() && dueDate.isBefore(reference);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

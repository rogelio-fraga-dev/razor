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
 * Comanda de um atendimento (V22): servicos + produtos na mesma conta, fechada de uma vez.
 * Mapeia {@code comandas}.
 *
 * <p>Enquanto {@link Status#ABERTA} nao tem total nem forma de pagamento — o valor so e
 * congelado no fechamento, que tambem cria <b>um unico</b> movimento de caixa.
 */
@Entity
@Table(name = "comandas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comanda {

    public enum Status {
        ABERTA,
        FECHADA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    /** Barbeiro do atendimento — e ele quem recebe a comissao dos produtos desta comanda. */
    @Column(name = "professional_id")
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Agendamento que originou a comanda (V28). Nulo = atendimento de balcao, sem marcacao.
     * Com vinculo, o financeiro sabe que o servico ja esta representado pelo agendamento e nao
     * conta duas vezes.
     */
    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "comanda_date", nullable = false)
    private LocalDate comandaDate;

    /** Congelado no fechamento. */
    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 12)
    private CashPaymentMethod paymentMethod;

    /** O unico movimento de caixa desta comanda. */
    @Column(name = "cash_movement_id")
    private UUID cashMovementId;

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

package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pedido de avaliacao pos-atendimento (V46): nasce quando a comanda fecha, sai pelo WhatsApp
 * depois do atraso configurado (dentro do expediente) e guarda a nota de 1 a 5 do cliente.
 *
 * <p>A linha tambem e a memoria das regras: "no maximo um pedido a cada 30 dias" olha as linhas
 * recentes do telefone, e {@link Status#OPTED_OUT} marca quem pediu para nao ser mais perguntado.
 */
@Entity
@Table(name = "customer_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "comanda_id")
    private UUID comandaId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "professional_id")
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;

    /** Nota de 1 a 5; nula enquanto o cliente nao respondeu. */
    @Column(name = "rating")
    private Integer rating;

    /** O que o cliente contou depois de uma nota baixa. */
    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
    }

    public enum Status {
        /** Programado: a pergunta ainda nao saiu. */
        SCHEDULED,
        /** Pergunta enviada, aguardando a nota. */
        SENT,
        /** Cliente deu a nota. */
        RATED,
        /** Ninguem respondeu dentro do prazo. */
        EXPIRED,
        /** Nao chegou a sair (regra mudou entre programar e enviar). */
        CANCELLED,
        /** Cliente pediu para nao receber mais pedidos de avaliacao. */
        OPTED_OUT
    }
}

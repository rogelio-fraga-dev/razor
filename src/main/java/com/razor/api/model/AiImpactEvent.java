package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fato do anti-falta que o agendamento nao guarda (V42): cancelar apaga o agendamento, entao
 * "desmarcou pela confirmacao" ou "a lista de espera ocupou a vaga" sumiriam junto. Alimenta o
 * card "O que a IA fez por voce". Sem FK para o agendamento de proposito.
 */
@Entity
@Table(name = "ai_impact_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImpactEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        }
    }

    public enum Type {
        /** Cliente confirmou presenca pela pergunta da vespera. */
        CONFIRMED,
        /** Cliente desmarcou pela pergunta da vespera: a vaga voltou com antecedencia. */
        DECLINED,
        /** Prazo da confirmacao venceu sem resposta (dono avisado). */
        NO_RESPONSE,
        /** Sem resposta e o tenant optou por liberar a vaga sozinho. */
        AUTO_RELEASED,
        /** Cliente da lista de espera aceitou a vaga liberada. */
        WAITLIST_REFILL
    }
}

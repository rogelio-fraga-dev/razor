package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incidente de saude (V50): um por (barbearia, tipo) enquanto aberto; repeticoes somam em
 * {@link #occurrences}. {@code barbershopId} nulo = incidente da plataforma.
 */
@Entity
@Table(name = "system_incident")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemIncident {

    public enum Status { OPEN, RESOLVED }

    @Id
    private UUID id;

    @Column(name = "barbershop_id")
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    /** Texto classificado por nos (nunca a mensagem crua do provedor). */
    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Builder.Default
    @Column(name = "occurrences", nullable = false)
    private int occurrences = 1;

    @Column(name = "owner_notified_at")
    private LocalDateTime ownerNotifiedAt;

    @Column(name = "operator_notified_at")
    private LocalDateTime operatorNotifiedAt;

    public boolean isOpen() {
        return status == Status.OPEN;
    }
}

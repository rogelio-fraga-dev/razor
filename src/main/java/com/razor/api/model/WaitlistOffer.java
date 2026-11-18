package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Oferta de uma vaga liberada a alguem da lista de espera (V47). Uma aberta por cliente e por vaga
 * (indices unicos parciais); o prazo ({@link #expiresAt}) vive no banco para sobreviver a restart.
 */
@Entity
@Table(name = "waitlist_offers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistOffer {

    public enum Status { OFFERED, ACCEPTED, DECLINED, EXPIRED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    /** Servico que sera marcado: o desejado pelo cliente ou o do horario que vagou. */
    @Column(name = "service", length = 255)
    private String service;

    /** Barbeiro da vaga (o do horario cancelado) ou o pedido pelo cliente. */
    @Column(name = "professional_id")
    private UUID professionalId;

    /** {@code true} = o cliente pediu ESTE barbeiro; outro livre nao serve. */
    @Builder.Default
    @Column(name = "professional_required", nullable = false)
    private boolean professionalRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE);
    }
}

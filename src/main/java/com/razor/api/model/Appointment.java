package com.razor.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste agendamento (Fase 1).
    @Column(name = "barbershop_id", nullable = false)
    private java.util.UUID barbershopId;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    // Profissional (barbeiro) responsavel — nullable ate ser atribuido (Fase 1 multiprofissional).
    @Column(name = "professional_id")
    private java.util.UUID professionalId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    @Column(name = "service")
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDENTE;

    @Column(name = "notified")
    @Builder.Default
    private boolean notified = false;

    @Column(name = "thanked")
    @Builder.Default
    private boolean thanked = false;

    /** Origem do agendamento (V38). Linhas antigas foram preenchidas com BOT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    @Builder.Default
    private AppointmentSource source = AppointmentSource.BOT;

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

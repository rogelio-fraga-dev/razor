package com.barberbot.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "appointment_date", nullable = false)
    private String appointmentDate; // DD/MM

    @Column(name = "appointment_time", nullable = false)
    private String appointmentTime; // HH:mm

    @Column(name = "service")
    private String service;

    @Column(name = "payment_status")
    @Builder.Default
    private String paymentStatus = "PENDENTE";

    @Column(name = "notified")
    @Builder.Default
    private boolean notified = false;

    @Column(name = "thanked")
    @Builder.Default
    private boolean thanked = false;

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

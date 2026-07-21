package com.razor.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Tenant desta entrada na lista de espera (Fase 1).
    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "preferred_day", nullable = false, length = 50)
    private String preferredDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

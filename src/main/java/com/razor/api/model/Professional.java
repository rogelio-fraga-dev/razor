package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profissional (barbeiro) de uma barbearia — base da agenda multiprofissional (Fase 1).
 */
@Entity
@Table(name = "professionals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

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

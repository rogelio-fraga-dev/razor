package com.razor.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Tenant dono deste cliente (Fase 1). O telefone e unico POR barbearia,
    // nao mais globalmente (ver constraint uq_customers_shop_phone na V4).
    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;
    
    @Column(name = "name", length = 255)
    private String name;
    
    // Novo campo: Controla até quando o bot deve ficar quieto (Modo Pausa)
    @Column(name = "paused_until")
    private LocalDateTime pausedUntil;
    
    @Builder.Default
    @Column(name = "fidelity_points")
    private Integer fidelityPoints = 0;
    
    @Column(name = "last_service_date")
    private LocalDateTime lastServiceDate;
    
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
    
    // Método auxiliar para saber se está pausado agora
    public boolean isPaused() {
        return pausedUntil != null && pausedUntil.isAfter(LocalDateTime.now());
    }
}
package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant do SaaS: uma barbearia. Todas as demais entidades pertencem a uma
 * barbearia via {@code barbershop_id} (Fase 1 — fundacao multi-tenant).
 */
@Entity
@Table(name = "barbershops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Barbershop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Identificador legivel/unico para URL do painel e do site (ex.: "figaro"). */
    @Column(nullable = false, unique = true)
    private String slug;

    /** Nome da instancia da Evolution API deste tenant (canal de WhatsApp). */
    @Column(name = "whatsapp_instance")
    private String whatsappInstance;

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

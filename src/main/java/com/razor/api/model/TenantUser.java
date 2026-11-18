package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usuario do painel web de uma barbearia (Fase 3). Autentica por email + senha (hash bcrypt);
 * o {@code barbershopId} define o tenant da sessao. Mapeia a tabela {@code tenant_users}.
 */
@Entity
@Table(name = "tenant_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column
    private String name;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.OWNER;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Versao das sessoes deste usuario: vai no JWT (claim {@code ver}) e o filtro so aceita token
     * com a versao vigente. Incrementar derruba todas as sessoes abertas (ex.: troca de senha).
     */
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

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

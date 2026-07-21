package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Telefone administrativo de uma barbearia (Fase 1). Um remetente cujo telefone consta aqui
 * e tratado como admin daquele tenant (roteamento admin vs cliente e notificacoes).
 * Mapeia a tabela {@code barbershop_admins}.
 */
@Entity
@Table(name = "barbershop_admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarbershopAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column
    private String name;

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

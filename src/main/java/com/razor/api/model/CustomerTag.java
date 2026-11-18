package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tag de cliente (V36): grupo livre definido pelo dono ("VIP", "Barba", "Indicou amigo") para
 * segmentar a base e mirar disparos. Mapeia {@code customer_tags}; o vinculo N:N com clientes
 * fica em {@link CustomerTagLink}.
 */
@Entity
@Table(name = "customer_tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTag {

    /** Cor usada quando o dono nao escolhe uma (cinza neutro). */
    public static final String DEFAULT_COLOR = "#6B7280";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false, length = 40)
    private String name;

    @Builder.Default
    @Column(nullable = false, length = 7)
    private String color = DEFAULT_COLOR;

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

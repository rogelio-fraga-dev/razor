package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuracoes do bot por barbearia (Fase 3): textos/links que o cliente recebe pelo WhatsApp.
 * 1:1 com {@code barbershops} (o id e a propria FK). Mapeia {@code barbershop_settings}.
 */
@Entity
@Table(name = "barbershop_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarbershopSettings {

    @Id
    @Column(name = "barbershop_id")
    private UUID barbershopId;

    @Column(name = "address_text", columnDefinition = "text")
    private String addressText;

    @Column(name = "address_maps_url", columnDefinition = "text")
    private String addressMapsUrl;

    @Column(name = "services_text", columnDefinition = "text")
    private String servicesText;

    @Column(name = "schedule_url", columnDefinition = "text")
    private String scheduleUrl;

    @Column(name = "instagram_url", columnDefinition = "text")
    private String instagramUrl;

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

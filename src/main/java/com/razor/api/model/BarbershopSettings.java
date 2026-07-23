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

    /** Liga/desliga o atendimento automatico de CLIENTES neste tenant (admins nao sao afetados). */
    @Builder.Default
    @Column(name = "bot_enabled", nullable = false)
    private Boolean botEnabled = Boolean.TRUE;

    // --- V15: identidade, pagamento, funcionamento e textos do bot (todos opcionais) ---

    /** Nome da barbearia como o bot fala com o cliente. */
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "pix_key")
    private String pixKey;

    @Column(name = "pix_copy_paste", columnDefinition = "text")
    private String pixCopyPaste;

    @Column(name = "payment_info", columnDefinition = "text")
    private String paymentInfo;

    /** "HH:mm" — inicio/fim do expediente usado na oferta de horarios. */
    @Column(name = "opening_time", length = 5)
    private String openingTime;

    @Column(name = "closing_time", length = 5)
    private String closingTime;

    /** Texto livre com os dias de funcionamento (ex.: "Seg a Sáb"). */
    @Column(name = "working_days", length = 120)
    private String workingDays;

    @Column(name = "welcome_text", columnDefinition = "text")
    private String welcomeText;

    @Column(name = "reminder_text", columnDefinition = "text")
    private String reminderText;

    @Column(name = "review_text", columnDefinition = "text")
    private String reviewText;

    @Column(name = "fidelity_reward_text", columnDefinition = "text")
    private String fidelityRewardText;

    @Column(name = "cancellation_policy", columnDefinition = "text")
    private String cancellationPolicy;

    /** Tom de voz/persona injetado no system prompt da IA. */
    @Column(name = "bot_persona", columnDefinition = "text")
    private String botPersona;

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

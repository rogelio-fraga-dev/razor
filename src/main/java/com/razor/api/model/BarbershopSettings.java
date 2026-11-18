package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
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

    /**
     * @deprecated desde a V32 — texto livre que ninguem interpretava. Use
     *             {@link #workingWeekdays}; a frase mostrada ao cliente e derivada dele.
     *             Mantido so para nao apagar dado antes de uma versao de confirmacao.
     */
    @Deprecated
    @Column(name = "working_days", length = 120)
    private String workingDays;

    /** Dias abertos, ISO 1=seg…7=dom separados por virgula (V32). Fonte da verdade do expediente. */
    @Column(name = "working_weekdays", length = 20)
    private String workingWeekdays;

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

    /** Quando o resumo de boas-vindas foi enviado ao dono (V17). NULL = ainda nao enviado. */
    @Column(name = "welcome_brief_sent_at")
    private LocalDateTime welcomeBriefSentAt;

    /** Meta de faturamento do mes definida pelo dono (V19). NULL = sem meta; o painel omite o card. */
    @Column(name = "monthly_revenue_goal", precision = 12, scale = 2)
    private BigDecimal monthlyRevenueGoal;

    /**
     * Quantos horarios FUTUROS um mesmo cliente pode ter ao mesmo tempo (V36). NULL = sem limite.
     * O default do banco e 2; ver {@code BarbershopSettingsService#getMaxFutureBookingsPerCustomer}.
     */
    @Builder.Default
    @Column(name = "max_future_bookings_per_customer")
    private Integer maxFutureBookingsPerCustomer = DEFAULT_MAX_FUTURE_BOOKINGS;

    /** Mesmo default da coluna (V36): vale tambem para o tenant que ainda nao tem linha. */
    public static final int DEFAULT_MAX_FUTURE_BOOKINGS = 2;

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

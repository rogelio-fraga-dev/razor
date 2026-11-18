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

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_TRIAL = "TRIAL";

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

    /**
     * ACTIVE | TRIAL (V49). TRIAL = cadastro pela landing, com {@link #trialEndsAt}. So
     * informativo enquanto a cobranca da assinatura nao existe: nada e bloqueado no fim do teste.
     */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(length = 120)
    private String city;

    /** Plano que o dono escolheu na landing ({@code ?plano=}): SOLO | EQUIPE | PRO. So interesse. */
    @Column(name = "signup_plan", length = 20)
    private String signupPlan;

    /** Passos do assistente guiado concluidos ou pulados, em CSV (V49). */
    @Column(name = "onboarding_steps_done", length = 200)
    private String onboardingStepsDone;

    /** Quando o dono terminou o assistente guiado. Nulo = ainda pode continuar pelo checklist. */
    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

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

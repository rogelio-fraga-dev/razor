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
 * Configuracao das automacoes da barbearia (V47), 1:1 com {@code barbershops}. Sem linha = os
 * padroes abaixo. Textos nulos = modelo padrao do sistema (ver {@code AutomationTemplates}).
 */
@Entity
@Table(name = "automation_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationSettings {

    public static final int DEFAULT_WAITLIST_WINDOW_MINUTES = 15;
    public static final int DEFAULT_LAPSED_DAYS = 30;
    public static final BigDecimal DEFAULT_BIRTHDAY_DISCOUNT = new BigDecimal("10");
    public static final int DEFAULT_BIRTHDAY_VALID_DAYS = 15;
    public static final int DEFAULT_FREQUENCY_CAP_DAYS = 7;
    /** ISO: 1 = segunda ... 7 = domingo. */
    public static final int DEFAULT_REPORT_DAY = 1;
    public static final int DEFAULT_REPORT_HOUR = 8;

    @Id
    @Column(name = "barbershop_id")
    private UUID barbershopId;

    @Builder.Default
    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled = true;

    @Builder.Default
    @Column(name = "waitlist_window_minutes", nullable = false)
    private int waitlistWindowMinutes = DEFAULT_WAITLIST_WINDOW_MINUTES;

    @Builder.Default
    @Column(name = "lapsed_enabled", nullable = false)
    private boolean lapsedEnabled = false;

    @Builder.Default
    @Column(name = "lapsed_days", nullable = false)
    private int lapsedDays = DEFAULT_LAPSED_DAYS;

    @Column(name = "lapsed_template", columnDefinition = "text")
    private String lapsedTemplate;

    @Builder.Default
    @Column(name = "birthday_enabled", nullable = false)
    private boolean birthdayEnabled = false;

    @Column(name = "birthday_template", columnDefinition = "text")
    private String birthdayTemplate;

    @Builder.Default
    @Column(name = "birthday_discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal birthdayDiscountPercent = DEFAULT_BIRTHDAY_DISCOUNT;

    @Builder.Default
    @Column(name = "birthday_coupon_valid_days", nullable = false)
    private int birthdayCouponValidDays = DEFAULT_BIRTHDAY_VALID_DAYS;

    @Builder.Default
    @Column(name = "return_enabled", nullable = false)
    private boolean returnEnabled = false;

    @Column(name = "return_template", columnDefinition = "text")
    private String returnTemplate;

    @Builder.Default
    @Column(name = "frequency_cap_days", nullable = false)
    private int frequencyCapDays = DEFAULT_FREQUENCY_CAP_DAYS;

    @Builder.Default
    @Column(name = "weekly_report_enabled", nullable = false)
    private boolean weeklyReportEnabled = true;

    @Builder.Default
    @Column(name = "weekly_report_day", nullable = false)
    private int weeklyReportDay = DEFAULT_REPORT_DAY;

    @Builder.Default
    @Column(name = "weekly_report_hour", nullable = false)
    private int weeklyReportHour = DEFAULT_REPORT_HOUR;

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

    /** Padroes do sistema para uma barbearia sem linha ainda (nao persistido). */
    public static AutomationSettings defaults(UUID barbershopId) {
        return AutomationSettings.builder().barbershopId(barbershopId).build();
    }

    /** Alguma campanha de marketing ligada? (A lista de espera e o relatorio nao contam.) */
    public boolean anyCampaignEnabled() {
        return lapsedEnabled || birthdayEnabled || returnEnabled;
    }
}

package com.razor.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Tenant desta entrada na lista de espera (Fase 1).
    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    /** Texto livre do dia que a IA mandou ("sexta", "25/11", "qualquer dia"). */
    @Column(name = "preferred_day", nullable = false, length = 50)
    private String preferredDay;

    /** Data exata desejada (V47). Nula = vale o dia da semana de {@link #preferredDay}, se houver. */
    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /** Periodo desejado (V47). Nulo = qualquer horario. */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_period", length = 10)
    private Period preferredPeriod;

    /** Servico desejado (V47). Nulo = o servico do horario que vagou. */
    @Column(name = "service", length = 255)
    private String service;

    /** Barbeiro desejado (V47). Nulo = qualquer um. */
    @Column(name = "professional_id")
    private UUID professionalId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.WAITING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** WAITING = esperando vaga; BOOKED = ocupou uma; CANCELLED = saiu da lista. */
    public enum Status { WAITING, BOOKED, CANCELLED }

    /** Faixa do dia: manha ate 12h, tarde ate 18h, noite dai em diante. */
    public enum Period {
        MANHA, TARDE, NOITE;

        public static Period of(LocalTime t) {
            if (t.getHour() < 12) return MANHA;
            if (t.getHour() < 18) return TARDE;
            return NOITE;
        }

        public String label() {
            return switch (this) {
                case MANHA -> "manhã";
                case TARDE -> "tarde";
                case NOITE -> "noite";
            };
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = Status.WAITING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

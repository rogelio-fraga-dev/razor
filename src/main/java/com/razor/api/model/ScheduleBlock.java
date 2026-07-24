package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Folga / bloqueio de agenda (V16). Impede a IA de oferecer ou marcar horario indisponivel.
 *
 * <p>Abrangencia: {@code professionalId} nulo = a barbearia inteira; preenchido = so aquele barbeiro.
 * <p>Quando: {@code blockDate} = data pontual; {@code weekday} (0=domingo … 6=sabado) = recorrente.
 * <p>Faixa: {@code startTime}/{@code endTime} nulos = dia inteiro.
 */
@Entity
@Table(name = "schedule_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    /** Nulo = vale para a barbearia toda. */
    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "block_date")
    private LocalDate blockDate;

    /** 0=domingo … 6=sabado (recorrente). */
    @Column(name = "weekday")
    private Short weekday;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    /** O bloqueio vale para este profissional (ou e da barbearia inteira)? */
    public boolean appliesTo(UUID candidateProfessionalId) {
        return professionalId == null || professionalId.equals(candidateProfessionalId);
    }

    /** O bloqueio incide nesta data (por data exata ou por dia da semana)? */
    public boolean appliesOn(LocalDate date) {
        if (blockDate != null) return blockDate.equals(date);
        if (weekday == null) return false;
        // DayOfWeek: 1=segunda … 7=domingo. Nosso weekday: 0=domingo … 6=sabado.
        int dow = date.getDayOfWeek().getValue() % 7;
        return dow == weekday.intValue();
    }

    /** O slot que comeca em {@code slotStart} e dura {@code durationMinutes} cai no bloqueio? */
    public boolean coversSlot(LocalTime slotStart, int durationMinutes) {
        if (startTime == null || endTime == null) return true; // dia inteiro
        LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
        // sobreposicao de intervalos: [slotStart, slotEnd) x [startTime, endTime)
        return slotStart.isBefore(endTime) && startTime.isBefore(slotEnd);
    }
}

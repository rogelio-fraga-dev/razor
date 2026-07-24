package com.razor.api.dto;

import com.razor.api.model.ScheduleBlock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Folga/bloqueio de agenda no painel. Ou {@code date} (pontual) ou {@code weekday} (recorrente,
 * 0=domingo … 6=sabado). Horarios nulos = dia inteiro. {@code professionalId} nulo = barbearia toda.
 */
public record ScheduleBlockPayload(
        UUID id,
        UUID professionalId,
        String professionalName,
        LocalDate date,
        Short weekday,
        LocalTime startTime,
        LocalTime endTime,
        String reason) {

    public static ScheduleBlockPayload from(ScheduleBlock b, String professionalName) {
        return new ScheduleBlockPayload(b.getId(), b.getProfessionalId(), professionalName,
                b.getBlockDate(), b.getWeekday(), b.getStartTime(), b.getEndTime(), b.getReason());
    }
}

package com.razor.api.dto;

import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.ConfirmationStatus;
import com.razor.api.model.DepositStatus;
import com.razor.api.model.PaymentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Tudo que a agenda interativa do painel precisa para desenhar um intervalo de dias (visao do dia
 * ou da semana) numa unica chamada: expediente, barbeiros, agendamentos e bloqueios ja expandidos
 * por data.
 *
 * <p>Os barbeiros vem aqui, e nao de {@code /api/admin/professionals}, porque aquela rota e so do
 * dono (tem comissao). Recepcao e barbeiro tambem usam a agenda e precisam das colunas.
 */
public record AgendaBoardResponse(
        LocalDate from,
        LocalDate to,
        String openingTime,
        String closingTime,
        /** Dias em que a barbearia abre, ISO 1=segunda … 7=domingo (V32). */
        List<Integer> openWeekdays,
        List<Pro> professionals,
        List<Item> appointments,
        List<Block> blocks) {

    /**
     * Barbeiro da coluna. Inclui inativos que ainda tem agendamento no intervalo ({@code active}
     * falso) — sem isso o horario sumia da grade. {@code photoUrl} e nulo enquanto o perfil do
     * profissional nao tiver foto.
     */
    public record Pro(UUID id, String name, boolean active, String photoUrl) {}

    /** Agendamento na grade. {@code comandaId} = comanda ja aberta para ele (V28), se houver. */
    public record Item(
            Long id,
            LocalDate date,
            LocalTime time,
            int durationMinutes,
            String customerName,
            String customerPhone,
            String service,
            UUID professionalId,
            String professionalName,
            PaymentStatus paymentStatus,
            AttendanceStatus attendanceStatus,
            boolean subscriber,
            UUID comandaId,
            String comandaStatus,
            /** Confirmacao da vespera (V42); null = nao foi perguntado. */
            ConfirmationStatus confirmationStatus,
            /** Sinal de quem falta muito (V42); null = nao exigido. */
            DepositStatus depositStatus) {}

    /**
     * Bloqueio (folga/almoco/feriado) incidindo numa data do intervalo. Recorrentes saem uma vez
     * por dia em que valem. {@code professionalId} nulo = barbearia inteira; horarios nulos = dia
     * inteiro.
     */
    public record Block(UUID id, UUID professionalId, LocalDate date, LocalTime startTime, LocalTime endTime,
                        String reason) {}
}

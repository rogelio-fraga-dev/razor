package com.razor.api.dto;

import com.razor.api.model.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Payloads da agenda interativa do painel. {@code notifyCustomer} nulo = avisar (padrao): quem
 * remarca pelo painel quase sempre quer que o cliente saiba; desligar e a excecao (ex.: o cliente
 * pediu a troca por telefone e ja sabe).
 */
public final class AgendaRequests {

    private AgendaRequests() {}

    /** Novo agendamento: barbeiro e horario escolhidos na grade; o servico vem do catalogo. */
    public record Create(
            @NotBlank(message = "Informe o nome do cliente.") String customerName,
            @NotBlank(message = "Informe o telefone do cliente.") String customerPhone,
            @NotNull(message = "Escolha o serviço.") UUID serviceId,
            @NotNull(message = "Escolha o barbeiro.") UUID professionalId,
            @NotNull(message = "Informe a data.") LocalDate date,
            @NotNull(message = "Informe o horário.") LocalTime time,
            Boolean notifyCustomer) {}

    /** Remarcar: nova data/hora e/ou outro barbeiro (arrastar na grade ou pelo formulario). */
    public record Move(
            @NotNull(message = "Informe a data.") LocalDate date,
            @NotNull(message = "Informe o horário.") LocalTime time,
            @NotNull(message = "Escolha o barbeiro.") UUID professionalId,
            Boolean notifyCustomer) {}

    public record Cancel(Boolean notifyCustomer) {}

    /** Comparecimento; {@code status} nulo volta para "agendado". */
    public record Attendance(AttendanceStatus status) {}

    public static boolean shouldNotify(Boolean flag) {
        return flag == null || flag;
    }
}

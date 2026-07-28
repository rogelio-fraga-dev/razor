package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Taxa de ocupacao das cadeiras no periodo: quanto do tempo disponivel virou atendimento.
 *
 * <p><b>Como as horas disponiveis sao contadas.</b> Para cada barbeiro ativo, somam-se os dias
 * <b>abertos</b> do periodo (V32) multiplicados pela jornada configurada
 * ({@code opening_time} a {@code closing_time}), descontando as folgas e bloqueios daquele
 * barbeiro (V16). Domingo de barbearia fechada nao entra — contar dia fechado como disponivel
 * derrubaria a taxa em cerca de 1/7 sem ninguem entender por que.
 *
 * <p><b>Horas vendidas</b> vem da duracao real de cada servico no catalogo: um combo de 60min
 * ocupa a cadeira o dobro de um corte de 30min, e contar "atendimentos" em vez de tempo faria
 * uma agenda de combos parecer mais vazia do que uma de cortes.
 *
 * <p>{@code unassignedAppointments} conta os agendamentos <b>sem barbeiro definido</b>. Eles
 * ocupam cadeira de verdade, mas nao da para atribuir a ninguem — aparecem a parte em vez de
 * serem distribuidos por chute, que inventaria ocupacao para quem talvez nao tenha atendido.
 *
 * <p>{@code rate} e nulo quando nao houve hora disponivel nenhuma (periodo so com dias fechados):
 * dividir por zero daria infinito, e {@code 0%} sugeriria uma agenda vazia que nunca existiu.
 */
public record OccupancyResponse(
        LocalDate from,
        LocalDate to,
        int openDays,
        BigDecimal availableHours,
        BigDecimal bookedHours,
        BigDecimal rate,
        int unassignedAppointments,
        List<ProfessionalOccupancy> professionals) {

    /** Ocupacao de um barbeiro. {@code rate} nulo pelo mesmo motivo do total. */
    public record ProfessionalOccupancy(
            UUID professionalId,
            String professionalName,
            BigDecimal availableHours,
            BigDecimal bookedHours,
            BigDecimal rate,
            int appointments) {
    }
}

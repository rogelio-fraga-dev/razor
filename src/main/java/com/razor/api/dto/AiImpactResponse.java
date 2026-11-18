package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "O que a IA fez por voce" no periodo [from, to] — o diferencial do produto em numeros. Tudo
 * medido, nada estimado: periodo sem dado devolve zero. Definicoes no {@code AiImpactService}.
 *
 * @param bot                     agendamentos criados pelo bot (WhatsApp/IA) e o valor deles
 * @param site                    agendamentos criados pelo site (agendamento online)
 * @param panel                   agendamentos criados no painel (referencia para comparar)
 * @param botMessages             mensagens que o bot respondeu
 * @param botMessagesOutsideHours dessas, quantas fora do expediente (dia fechado ou fora do horario)
 * @param rescuedCustomers        clientes que estavam "sumidos" e voltaram a marcar pelo bot
 * @param confirmationsReceived   confirmacoes "1" recebidas na pergunta da vespera
 * @param slotsFreedInAdvance     horarios desmarcados pela confirmacao (o cliente avisou que nao vinha)
 * @param autoReleased            horarios liberados sozinhos por falta de resposta (se ligado)
 * @param waitlistRefills         vagas liberadas que a lista de espera ocupou
 * @param noShowsAvoided          faltas evitadas = {@code slotsFreedInAdvance}: cada um e um cliente
 *                                que disse que nao vinha antes da hora, em vez de deixar a cadeira vazia
 */
public record AiImpactResponse(
        LocalDate from,
        LocalDate to,
        Channel bot,
        Channel site,
        Channel panel,
        long botMessages,
        long botMessagesOutsideHours,
        long rescuedCustomers,
        long confirmationsReceived,
        long slotsFreedInAdvance,
        long autoReleased,
        long waitlistRefills,
        long noShowsAvoided) {

    /** Quantidade de agendamentos de uma origem e o valor de tabela deles. */
    public record Channel(long appointments, BigDecimal revenue) {
    }
}

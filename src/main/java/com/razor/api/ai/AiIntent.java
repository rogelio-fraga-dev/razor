package com.razor.api.ai;

/**
 * Intencao extraida da resposta do modelo de IA.
 *
 * <p>A IA embute "tags" tecnicas no texto (ex.: {@code [NOVO_AGENDAMENTO|Joao|28/05|10:00|Corte]}).
 * Este tipo selado representa cada intencao de forma estruturada, eliminando o acesso cru a
 * indices de array que antes quebrava com {@code ArrayIndexOutOfBoundsException} quando o
 * modelo devolvia uma tag malformada.
 *
 * <p>Todo caso carrega {@link #cleanedResponse()}: o texto a ser enviado ao cliente, ja sem a tag.
 */
public sealed interface AiIntent
        permits AiIntent.NovoAgendamento, AiIntent.ListaDeEspera, AiIntent.Cancelar, AiIntent.Resposta {

    /** Texto para enviar ao cliente, com a tag tecnica removida. */
    String cleanedResponse();

    /** Agendamento novo solicitado pela IA. */
    record NovoAgendamento(String name, String date, String time, String service, String cleanedResponse)
            implements AiIntent {
    }

    /** Inclusao na lista de espera para um dia preferido. */
    record ListaDeEspera(String name, String preferredDay, String cleanedResponse) implements AiIntent {
    }

    /** Cancelamento do agendamento ativo do cliente. */
    record Cancelar(String cleanedResponse) implements AiIntent {
    }

    /** Nenhuma tag acionavel: apenas conversa/resposta normal. */
    record Resposta(String cleanedResponse) implements AiIntent {
    }
}

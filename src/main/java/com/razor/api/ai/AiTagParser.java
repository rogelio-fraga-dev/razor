package com.razor.api.ai;

/**
 * Extrai a {@link AiIntent} da resposta bruta do modelo de IA.
 *
 * <p>Robusto por design: tags ausentes, incompletas ou sem colchete de fechamento nao
 * lancam excecao — caem em {@link AiIntent.Resposta}, tratando o texto como conversa normal.
 * Isso substitui o parsing anterior baseado em {@code split("\\|")} + acesso a {@code parts[i]},
 * que quebrava com respostas malformadas do GPT.
 */
@lombok.extern.slf4j.Slf4j
public class AiTagParser {

    /**
     * Resposta ao cliente quando a IA tentou agendar mas a tag veio malformada. Nada foi
     * agendado, entao o texto da IA ("Agendamento Confirmado!") NAO pode ir para o cliente.
     */
    public static final String MALFORMED_BOOKING_MSG = "Quase lá! 😊 Para eu registrar certinho, "
            + "me confirma por favor o *dia*, o *horário* e o *serviço* que você quer?";

    private static final String TAG_AGENDAMENTO = "[NOVO_AGENDAMENTO|";
    private static final String TAG_LISTA_ESPERA = "[LISTA_DE_ESPERA|";
    private static final String TAG_CANCELAR = "[CANCELAR_AGENDAMENTO]";
    private static final String TAG_SERVICO = "[SERVICO|";

    /**
     * Servico que a IA identificou numa escolha indireta do cliente ("o segundo", "os dois"):
     * {@code [SERVICO|Corte + Barba]}. Nao e uma intencao — so informa a conversa qual servico vale
     * para calcular os horarios livres da proxima resposta. {@code null} quando nao ha a tag.
     */
    public static String extractService(String aiResponse) {
        if (aiResponse == null) return null;
        int start = aiResponse.indexOf(TAG_SERVICO);
        if (start < 0) return null;
        int end = aiResponse.indexOf(']', start);
        if (end < 0) return null;
        String value = aiResponse.substring(start + TAG_SERVICO.length(), end).trim();
        return value.isEmpty() ? null : value;
    }

    /** Remove toda tag {@code [SERVICO|...]} do texto (o cliente nunca ve tag tecnica). */
    public static String removeServiceTag(String aiResponse) {
        if (aiResponse == null) return null;
        return aiResponse.replaceAll("\\[SERVICO\\|[^\\]]*\\]", "").replaceAll("[ \\t]+\\n", "\n").trim();
    }

    public AiIntent parse(String aiResponse) {
        aiResponse = removeServiceTag(aiResponse);
        if (aiResponse == null || aiResponse.isBlank()) {
            return new AiIntent.Resposta(aiResponse == null ? "" : aiResponse);
        }

        if (aiResponse.contains(TAG_AGENDAMENTO)) {
            AiIntent parsed = parseAgendamento(aiResponse);
            if (parsed != null) {
                return parsed;
            }
            // Tag de agendamento malformada (sem "]" ou com campos faltando): antes o texto ia
            // cru para o cliente — com a tag tecnica — e nada era agendado, sem log nenhum.
            log.warn("[IA] Tag de agendamento malformada, nada foi agendado: '{}'",
                    malformedFragment(aiResponse, TAG_AGENDAMENTO));
            return new AiIntent.Resposta(MALFORMED_BOOKING_MSG);
        }

        if (aiResponse.contains(TAG_LISTA_ESPERA)) {
            AiIntent parsed = parseListaEspera(aiResponse);
            if (parsed != null) {
                return parsed;
            }
        }

        if (aiResponse.contains(TAG_CANCELAR)) {
            String cleaned = aiResponse.replace(TAG_CANCELAR, "").trim();
            return new AiIntent.Cancelar(cleaned);
        }

        return new AiIntent.Resposta(aiResponse.trim());
    }

    private AiIntent parseAgendamento(String aiResponse) {
        String tag = extractTag(aiResponse, TAG_AGENDAMENTO);
        if (tag == null) {
            return null;
        }
        String[] parts = fields(tag);
        // Esperado: NOVO_AGENDAMENTO | name | date | time | service [| barbeiro]  -> 5 ou 6 campos
        if (parts.length < 5) {
            return null;
        }
        String professional = parts.length >= 6 && !parts[5].isBlank() ? parts[5].trim() : null;
        String cleaned = aiResponse.replace(tag, "").trim();
        return new AiIntent.NovoAgendamento(
                parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim(), professional, cleaned);
    }

    private AiIntent parseListaEspera(String aiResponse) {
        String tag = extractTag(aiResponse, TAG_LISTA_ESPERA);
        if (tag == null) {
            return null;
        }
        String[] parts = fields(tag);
        // Esperado: LISTA_DE_ESPERA | name | preferredDay [| periodo | servico | barbeiro] -> 3 a 6
        // campos (os tres ultimos opcionais, V47).
        if (parts.length < 3) {
            return null;
        }
        String cleaned = aiResponse.replace(tag, "").trim();
        return new AiIntent.ListaDeEspera(parts[1].trim(), parts[2].trim(), cleaned,
                optional(parts, 3), optional(parts, 4), optional(parts, 5));
    }

    private static String optional(String[] parts, int i) {
        if (parts.length <= i) return null;
        String v = parts[i].trim();
        return v.isEmpty() ? null : v;
    }

    /** Extrai a tag completa (inclusive colchetes) a partir do marcador ate o {@code ]}. */
    private String extractTag(String aiResponse, String opener) {
        int start = aiResponse.indexOf(opener);
        if (start < 0) {
            return null;
        }
        int end = aiResponse.indexOf(']', start);
        if (end < 0) {
            return null; // sem fechamento -> tag malformada
        }
        return aiResponse.substring(start, end + 1);
    }

    /** Trecho da tag malformada (do marcador ate o "]" ou o fim do texto), para o log. */
    private String malformedFragment(String aiResponse, String opener) {
        int start = aiResponse.indexOf(opener);
        int end = aiResponse.indexOf(']', start);
        return aiResponse.substring(start, end < 0 ? aiResponse.length() : end + 1);
    }

    /** Remove os colchetes e separa os campos por barra vertical. */
    private String[] fields(String tag) {
        return tag.replace("[", "").replace("]", "").split("\\|");
    }
}

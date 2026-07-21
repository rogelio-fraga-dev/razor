package com.razor.api.ai;

/**
 * Extrai a {@link AiIntent} da resposta bruta do modelo de IA.
 *
 * <p>Robusto por design: tags ausentes, incompletas ou sem colchete de fechamento nao
 * lancam excecao — caem em {@link AiIntent.Resposta}, tratando o texto como conversa normal.
 * Isso substitui o parsing anterior baseado em {@code split("\\|")} + acesso a {@code parts[i]},
 * que quebrava com respostas malformadas do GPT.
 */
public class AiTagParser {

    private static final String TAG_AGENDAMENTO = "[NOVO_AGENDAMENTO|";
    private static final String TAG_LISTA_ESPERA = "[LISTA_DE_ESPERA|";
    private static final String TAG_CANCELAR = "[CANCELAR_AGENDAMENTO]";

    public AiIntent parse(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return new AiIntent.Resposta(aiResponse == null ? "" : aiResponse);
        }

        if (aiResponse.contains(TAG_AGENDAMENTO)) {
            AiIntent parsed = parseAgendamento(aiResponse);
            if (parsed != null) {
                return parsed;
            }
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
        // Esperado: NOVO_AGENDAMENTO | name | date | time | service  -> 5 campos
        if (parts.length < 5) {
            return null;
        }
        String cleaned = aiResponse.replace(tag, "").trim();
        return new AiIntent.NovoAgendamento(
                parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim(), cleaned);
    }

    private AiIntent parseListaEspera(String aiResponse) {
        String tag = extractTag(aiResponse, TAG_LISTA_ESPERA);
        if (tag == null) {
            return null;
        }
        String[] parts = fields(tag);
        // Esperado: LISTA_DE_ESPERA | name | preferredDay  -> 3 campos
        if (parts.length < 3) {
            return null;
        }
        String cleaned = aiResponse.replace(tag, "").trim();
        return new AiIntent.ListaDeEspera(parts[1].trim(), parts[2].trim(), cleaned);
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

    /** Remove os colchetes e separa os campos por barra vertical. */
    private String[] fields(String tag) {
        return tag.replace("[", "").replace("]", "").split("\\|");
    }
}

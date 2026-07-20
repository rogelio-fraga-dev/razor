package com.razor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiTagParserTest {

    private final AiTagParser parser = new AiTagParser();

    @Test
    @DisplayName("resposta simples sem tag vira Resposta")
    void parse_plainText_returnsResposta() {
        AiIntent intent = parser.parse("Claro! Qual horario voce prefere?");

        assertThat(intent).isInstanceOf(AiIntent.Resposta.class);
        assertThat(intent.cleanedResponse()).isEqualTo("Claro! Qual horario voce prefere?");
    }

    @Test
    @DisplayName("tag de agendamento valida extrai os campos e limpa o texto")
    void parse_validAgendamento_returnsNovoAgendamento() {
        String raw = "Perfeito! [NOVO_AGENDAMENTO|Joao Silva|28/05|10:00|Corte] Te espero.";

        AiIntent intent = parser.parse(raw);

        assertThat(intent).isInstanceOf(AiIntent.NovoAgendamento.class);
        AiIntent.NovoAgendamento na = (AiIntent.NovoAgendamento) intent;
        assertThat(na.name()).isEqualTo("Joao Silva");
        assertThat(na.date()).isEqualTo("28/05");
        assertThat(na.time()).isEqualTo("10:00");
        assertThat(na.service()).isEqualTo("Corte");
        assertThat(na.cleanedResponse()).doesNotContain("NOVO_AGENDAMENTO").contains("Perfeito!");
    }

    @Test
    @DisplayName("tag de lista de espera valida extrai nome e dia")
    void parse_validListaEspera_returnsListaDeEspera() {
        String raw = "Anotei! [LISTA_DE_ESPERA|Maria|sabado]";

        AiIntent intent = parser.parse(raw);

        assertThat(intent).isInstanceOf(AiIntent.ListaDeEspera.class);
        AiIntent.ListaDeEspera le = (AiIntent.ListaDeEspera) intent;
        assertThat(le.name()).isEqualTo("Maria");
        assertThat(le.preferredDay()).isEqualTo("sabado");
        assertThat(le.cleanedResponse()).doesNotContain("LISTA_DE_ESPERA");
    }

    @Test
    @DisplayName("tag de cancelamento vira Cancelar")
    void parse_cancelar_returnsCancelar() {
        AiIntent intent = parser.parse("Tudo bem, cancelei. [CANCELAR_AGENDAMENTO]");

        assertThat(intent).isInstanceOf(AiIntent.Cancelar.class);
        assertThat(intent.cleanedResponse()).doesNotContain("CANCELAR_AGENDAMENTO").contains("cancelei");
    }

    @Test
    @DisplayName("tag de agendamento incompleta NAO quebra (regressao do ArrayIndexOutOfBounds)")
    void parse_malformedAgendamento_doesNotThrow_fallsBack() {
        // Faltam campos (so 3 de 5) - antes quebrava com ArrayIndexOutOfBoundsException
        AiIntent intent = parser.parse("[NOVO_AGENDAMENTO|Joao|28/05]");

        assertThat(intent).isNotInstanceOf(AiIntent.NovoAgendamento.class);
        assertThat(intent).isInstanceOf(AiIntent.Resposta.class);
    }

    @Test
    @DisplayName("tag sem colchete de fechamento NAO quebra")
    void parse_unclosedTag_doesNotThrow_fallsBack() {
        AiIntent intent = parser.parse("[NOVO_AGENDAMENTO|Joao|28/05|10:00|Corte");

        assertThat(intent).isInstanceOf(AiIntent.Resposta.class);
    }

    @Test
    @DisplayName("null vira Resposta vazia sem quebrar")
    void parse_null_returnsEmptyResposta() {
        AiIntent intent = parser.parse(null);

        assertThat(intent).isInstanceOf(AiIntent.Resposta.class);
        assertThat(intent.cleanedResponse()).isEmpty();
    }
}

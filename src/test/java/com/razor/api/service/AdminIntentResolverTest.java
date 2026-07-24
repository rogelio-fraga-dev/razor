package com.razor.api.service;

import static com.razor.api.service.AdminIntentResolver.AdminIntent.*;
import static com.razor.api.service.AdminIntentResolver.resolve;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * O dono nao deve precisar decorar o menu: fala natural tem de virar intencao.
 * Estes casos sao frases que um barbeiro realmente escreveria/falaria.
 */
class AdminIntentResolverTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // fala natural do dia a dia
            "'agenda de hoje',                    AGENDA_HOJE",
            "'quem vem hoje?',                    AGENDA_HOJE",
            "'me mostra os clientes de hoje',     AGENDA_HOJE",
            "'como ta a agenda da semana',        AGENDA_SEMANA",
            "'manda um aviso',                    AVISO",
            "'quero avisar todo mundo',           AVISO",
            "'disparar promoção',                 PROMOCAO",
            "'manda uma oferta pra base',         PROMOCAO",
            "'agendar disparo pra amanhã',        AGENDAR_DISPARO",
            "'programar disparo',                 AGENDAR_DISPARO",
            "'pausar cliente',                    PAUSAR",
            "'silenciar o bot pra esse numero',   PAUSAR",
            "'retomar o bot',                     RETOMAR",
            "'listar clientes',                   LISTAR_CLIENTES",
            "'me ve a base de clientes',          LISTAR_CLIENTES",
            "'apagar um cliente',                 APAGAR_CLIENTE",
            "'como funciona isso',                AJUDA",
            // atalhos numericos continuam valendo
            "'1',                                 AGENDA_HOJE",
            "'5',                                 AGENDAR_DISPARO",
            "'10',                                AJUDA",
            // menu explicito
            "'menu',                              MENU",
            "'quais as opções?',                  MENU",
    })
    @DisplayName("frases naturais viram a intenção certa")
    void naturalPhrases(String frase, AdminIntentResolver.AdminIntent esperado) {
        assertThat(resolve(frase)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oi", "Olá", "opa", "bom dia", "boa noite", "e aí", "fala chefe", "tudo bem?"})
    @DisplayName("saudações são reconhecidas (e não caem em 'não entendi')")
    void greetings(String frase) {
        assertThat(resolve(frase)).isEqualTo(SAUDACAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quanto faturei hoje", "resumo financeiro", "fechamento do caixa",
            "comissões do Diego", "me ve o DRE"
    })
    @DisplayName("assunto financeiro sempre aponta para o painel web")
    void financeGoesToPanel(String frase) {
        assertThat(resolve(frase)).isEqualTo(PAINEL_WEB);
    }

    @Test
    @DisplayName("acento e pontuação não atrapalham")
    void accentInsensitive() {
        assertThat(resolve("PROMOÇÃO!!!")).isEqualTo(PROMOCAO);
        assertThat(resolve("agenda da semana???")).isEqualTo(AGENDA_SEMANA);
    }

    @Test
    @DisplayName("'agendar disparo' não é confundido com a agenda do dia")
    void scheduleBroadcastIsNotAgenda() {
        assertThat(resolve("agendar disparo")).isEqualTo(AGENDAR_DISPARO);
        assertThat(resolve("agenda")).isEqualTo(AGENDA_HOJE);
    }

    @Test
    @DisplayName("'fechamento de caixa' vai para o painel, não para a agenda")
    void cashClosingIsNotAgenda() {
        assertThat(resolve("quero o fechamento do dia")).isEqualTo(PAINEL_WEB);
    }

    @Test
    @DisplayName("texto solto vira DESCONHECIDO (o fluxo então ensina os comandos)")
    void unknownStaysUnknown() {
        assertThat(resolve("blablabla xyz")).isEqualTo(DESCONHECIDO);
        assertThat(resolve("")).isEqualTo(DESCONHECIDO);
        assertThat(resolve(null)).isEqualTo(DESCONHECIDO);
    }

    @Test
    @DisplayName("isCommand: saudação e ditado livre NÃO são comando (áudio vira transcrição)")
    void isCommandExcludesGreetingAndFreeText() {
        assertThat(AdminIntentResolver.isCommand("agenda de hoje")).isTrue();
        assertThat(AdminIntentResolver.isCommand("oi")).isFalse();
        assertThat(AdminIntentResolver.isCommand("lembrar de comprar pomada")).isFalse();
    }
}

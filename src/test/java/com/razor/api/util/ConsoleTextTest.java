package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsoleTextTest {

    @Test
    @DisplayName("remove acentos preservando as letras base")
    void sanitize_removesAccents() {
        assertThat(ConsoleText.sanitize("Configuração de Sessão")).isEqualTo("Configuracao de Sessao");
    }

    @Test
    @DisplayName("remove emojis e caracteres nao-ASCII")
    void sanitize_removesEmoji() {
        assertThat(ConsoleText.sanitize("Ola 💈 mundo")).isEqualTo("Ola  mundo");
    }

    @Test
    @DisplayName("null vira string vazia")
    void sanitize_null() {
        assertThat(ConsoleText.sanitize(null)).isEmpty();
    }

    @Test
    @DisplayName("faz trim das bordas")
    void sanitize_trims() {
        assertThat(ConsoleText.sanitize("  texto  ")).isEqualTo("texto");
    }
}

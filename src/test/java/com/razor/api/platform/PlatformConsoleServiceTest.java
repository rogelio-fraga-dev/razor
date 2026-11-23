package com.razor.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.razor.api.config.RazorProperties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformConsoleServiceTest {

    private final RazorProperties props = new RazorProperties();
    private final PlatformConsoleService service = new PlatformConsoleService(mock(JdbcTemplate.class), props);

    @Test
    @DisplayName("custo: preco por modelo (nome exato ou prefixo mais longo), audio por chamada, modelo desconhecido = 0")
    void cost() {
        // 1M de entrada + 1M de saida no mini = 0,15 + 0,60
        assertThat(service.costUsd("gpt-4o-mini", "CHAT", 10, 1_000_000, 1_000_000)).isEqualTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(service.priceFor("gpt-4o-mini-2024-07-18")).containsExactly(0.15, 0.60);
        assertThat(service.priceFor("gpt-4o-2024-08-06")).containsExactly(2.50, 10.00);
        assertThat(service.costUsd("gpt-4o-mini-transcribe", "TRANSCRIBE", 10, 0, 0)).isEqualTo(0.03, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(service.costUsd("modelo-x", "CHAT", 1, 1000, 1000)).isZero();
    }

    @Test
    @DisplayName("telefone mascarado na lista (so os 4 ultimos)")
    void mask() {
        assertThat(PlatformConsoleService.mask("55 (11) 98888-7777")).isEqualTo("•••• 7777");
        assertThat(PlatformConsoleService.mask("12")).isEqualTo("••••");
        assertThat(PlatformConsoleService.mask(null)).isEmpty();
    }

    @Test
    @DisplayName("heuristica de cliente irritado (a mesma regex vai para o Postgres; aqui conferida em Java)")
    void angryPattern() {
        // \m e \M (limite de palavra do Postgres) viram \b no Java para o teste.
        Pattern p = Pattern.compile(PlatformConsoleService.ANGRY_PATTERN.replace("\\m", "\\b").replace("\\M", "\\b"),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        assertThat(p.matcher("Que absurdo, ninguém responde aqui").find()).isTrue();
        assertThat(p.matcher("atendimento péssimo").find()).isTrue();
        assertThat(p.matcher("vou no PROCON").find()).isTrue();
        assertThat(p.matcher("isso é lixo").find()).isTrue();
        assertThat(p.matcher("quero marcar um corte amanhã").find()).isFalse();
        assertThat(p.matcher("o lixeiro passou").find()).as("palavra inteira").isFalse();
    }

    @Test
    @DisplayName("janela de dias limitada a 1..90")
    void clamp() {
        assertThat(PlatformConsoleService.clampDays(0)).isEqualTo(1);
        assertThat(PlatformConsoleService.clampDays(400)).isEqualTo(90);
    }
}

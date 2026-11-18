package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.razor.api.dto.SiteContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Conteúdo por seção do site (V45)")
class SiteContentPolicyTest {

    private static Map<String, Object> sections(Map<String, Object> s) {
        Map<String, Object> m = new HashMap<>();
        m.put("sections", s);
        return m;
    }

    @Test
    @DisplayName("normaliza: apara, vazio vira ausente, visible=true some, motion full some")
    void normalizes() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("sections", Map.of(
                "services", Map.of("eyebrow", "  A carta ", "title", "", "visible", true),
                "gallery", Map.of("visible", false),
                "team", Map.of("title", "   ")));
        raw.put("bookLabel", " Marcar agora ");
        raw.put("motion", "full");

        SiteContent c = SiteContentPolicy.normalize(raw);

        assertThat(c.sections()).containsOnlyKeys("services", "gallery");
        assertThat(c.sections().get("services")).isEqualTo(new SiteContent.Section(null, "A carta", null, null));
        assertThat(c.sections().get("gallery").visible()).isFalse();
        assertThat(c.bookLabel()).isEqualTo("Marcar agora");
        assertThat(c.motion()).isNull();
    }

    @Test
    @DisplayName("nada preenchido = nulo (tudo no padrão do template)")
    void emptyIsNull() {
        assertThat(SiteContentPolicy.normalize(null)).isNull();
        assertThat(SiteContentPolicy.normalize(Map.of())).isNull();
        assertThat(SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("text", " "))))).isNull();
    }

    @Test
    @DisplayName("recusa seção, campo e chave desconhecidos")
    void rejectsUnknown() {
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("rodape", Map.of("title", "x")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rodape");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("html", "<b>x</b>")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("html");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(Map.of("script", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteContentPolicy.normalize(Map.of("sections", List.of("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recusa tipo errado: texto que não é texto, visible que não é booleano")
    void rejectsWrongTypes() {
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("title", 42)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("visible", "false")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", "texto solto"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("limites: olho 60, título 120, texto 600, botão 40 — no limite passa, acima recusa")
    void limits() {
        assertThat(SiteContentPolicy.normalize(sections(Map.of("cta", Map.of(
                "eyebrow", "a".repeat(60), "title", "b".repeat(120), "text", "c".repeat(600))))))
                .isNotNull();
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("eyebrow", "a".repeat(61))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("60");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("title", "b".repeat(121))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(sections(Map.of("cta", Map.of("text", "c".repeat(601))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("600");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(Map.of("bookLabel", "d".repeat(41))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("40");
    }

    @Test
    @DisplayName("motion só full, subtle ou off")
    void motion() {
        assertThat(SiteContentPolicy.normalize(Map.of("motion", "subtle")).motion()).isEqualTo("subtle");
        assertThat(SiteContentPolicy.normalize(Map.of("motion", "off")).motion()).isEqualTo("off");
        assertThatThrownBy(() -> SiteContentPolicy.normalize(Map.of("motion", "turbo")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tira caracteres de controle e invisíveis; quebra de linha só no texto")
    void stripsControlChars() {
        SiteContent c = SiteContentPolicy.normalize(sections(Map.of("cta", Map.of(
                "title", "Bora\u0000 ‮marcar\nhoje",
                "text", "Linha 1\r\nLinha 2\u0007"))));
        assertThat(c.sections().get("cta").title()).isEqualTo("Bora marcar hoje");
        assertThat(c.sections().get("cta").text()).isEqualTo("Linha 1\nLinha 2");
    }

    @Test
    @DisplayName("ida e volta pela coluna JSON preserva o conteúdo; JSON corrompido vira padrão")
    void jsonRoundTrip() {
        SiteContent c = SiteContentPolicy.normalize(Map.of(
                "sections", Map.of("plans", Map.of("visible", false, "title", "Clube")),
                "bookLabel", "Quero cortar",
                "motion", "off"));
        String json = SiteContentPolicy.toJson(c);
        assertThat(json).doesNotContain("null");
        assertThat(SiteContentPolicy.fromJson(json)).isEqualTo(c);
        assertThat(SiteContentPolicy.asMap(c)).containsEntry("bookLabel", "Quero cortar");
        assertThat(SiteContentPolicy.fromJson("{quebrado")).isNull();
        assertThat(SiteContentPolicy.fromJson(null)).isNull();
    }
}

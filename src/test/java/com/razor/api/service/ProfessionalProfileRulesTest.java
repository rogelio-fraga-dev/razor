package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Perfil do barbeiro — validação (V37)")
class ProfessionalProfileRulesTest {

    /** Bytes que começam como JPEG de verdade (FF D8 FF), com o tamanho pedido. */
    static byte[] jpegBytes(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 0x11);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }

    static String jpegDataUri(int size) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes(size));
    }

    @Test
    @DisplayName("aceita data URI JPEG pequeno")
    void photo_validJpeg_accepted() {
        String uri = jpegDataUri(30 * 1024);
        assertThat(ProfessionalProfileRules.validatePhoto(uri)).isEqualTo(uri);
    }

    @Test
    @DisplayName("aceita PNG com assinatura correta")
    void photo_validPng_accepted() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        assertThat(ProfessionalProfileRules.validatePhoto(uri)).isEqualTo(uri);
    }

    @Test
    @DisplayName("vazio ou nulo = sem foto")
    void photo_blank_isNull() {
        assertThat(ProfessionalProfileRules.validatePhoto(null)).isNull();
        assertThat(ProfessionalProfileRules.validatePhoto("  ")).isNull();
    }

    @Test
    @DisplayName("recusa foto acima de 60KB")
    void photo_tooLarge_rejected() {
        assertThatThrownBy(() -> ProfessionalProfileRules.validatePhoto(jpegDataUri(61 * 1024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60KB");
        // e não decodifica lixo gigante
        assertThatThrownBy(() -> ProfessionalProfileRules.validatePhoto(jpegDataUri(2 * 1024 * 1024)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/foto.jpg",
            "http://cdn.example/a.png",
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "data:image/svg+xml;base64,PHN2Zy8+",
            "data:image/jpeg,naoebase64",
            "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
            "DATA:image/jpeg;base64,/9j/AAAA",
            "data:image/jpeg;base64,/9j/@@@@",
            "file:///etc/passwd",
    })
    @DisplayName("recusa qualquer coisa que não seja data URI jpeg/png/webp em base64")
    void photo_otherSchemes_rejected(String value) {
        assertThatThrownBy(() -> ProfessionalProfileRules.validatePhoto(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recusa conteúdo que não é a imagem declarada (assinatura não bate)")
    void photo_magicMismatch_rejected() {
        String fakeJpeg = "data:image/jpeg;base64,"
                + Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes());
        assertThatThrownBy(() -> ProfessionalProfileRules.validatePhoto(fakeJpeg))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Instagram: aceita https, normaliza @perfil e recusa outros esquemas")
    void instagram_rules() {
        assertThat(ProfessionalProfileRules.validateInstagram("https://instagram.com/diego.barber"))
                .isEqualTo("https://instagram.com/diego.barber");
        assertThat(ProfessionalProfileRules.validateInstagram("@diego.barber"))
                .isEqualTo("https://instagram.com/diego.barber");
        assertThat(ProfessionalProfileRules.validateInstagram(" ")).isNull();
        for (String bad : List.of("javascript:alert(1)", "ftp://x.com/a", "data:text/html,oi", "https://", "não é link")) {
            assertThatThrownBy(() -> ProfessionalProfileRules.validateInstagram(bad))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("especialidades: aparadas, sem duplicata, sem quebra de linha, com limite")
    void specialties_normalized() {
        assertThat(ProfessionalProfileRules.normalizeSpecialties(List.of(" Degradê ", "degradê", "Bar\nba", "")))
                .isEqualTo("Degradê\nBar ba");
        assertThat(ProfessionalProfileRules.normalizeSpecialties(List.of())).isNull();
        assertThatThrownBy(() -> ProfessionalProfileRules.normalizeSpecialties(
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "i")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

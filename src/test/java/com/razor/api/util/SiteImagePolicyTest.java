package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fotos do site: validação pelos bytes (V45)")
class SiteImagePolicyTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0};

    static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    @DisplayName("JPEG, PNG e WebP passam com o tipo detectado")
    void acceptsRaster() {
        assertThat(SiteImagePolicy.decode("data:image/jpeg;base64," + b64(JPEG)).contentType()).isEqualTo("image/jpeg");
        assertThat(SiteImagePolicy.decode(b64(PNG)).contentType()).isEqualTo("image/png");
        assertThat(SiteImagePolicy.decode("data:image/webp;base64," + b64(WEBP)).contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("o tipo declarado não manda: PNG declarado como JPEG sai como PNG")
    void declaredTypeIgnored() {
        var d = SiteImagePolicy.decode("data:image/jpeg;base64," + b64(PNG));
        assertThat(d.contentType()).isEqualTo("image/png");
        assertThat(d.bytes()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("SVG, HTML e GIF disfarçados de imagem são recusados")
    void rejectsNonRaster() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        assertThatThrownBy(() -> SiteImagePolicy.decode("data:image/svg+xml;base64," + b64(svg.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG");
        assertThatThrownBy(() -> SiteImagePolicy.decode("data:image/jpeg;base64," + b64("<html>oi</html>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteImagePolicy.decode(b64("GIF89a....".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        // RIFF que nao e WebP (ex.: WAV)
        byte[] wav = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
        assertThatThrownBy(() -> SiteImagePolicy.decode(b64(wav))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("acima de 2MB é recusado, mesmo com assinatura de JPEG")
    void rejectsTooBig() {
        byte[] big = Arrays.copyOf(JPEG, SiteImagePolicy.MAX_BYTES + 1);
        assertThatThrownBy(() -> SiteImagePolicy.decode(b64(big)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2MB");
        byte[] limit = Arrays.copyOf(JPEG, SiteImagePolicy.MAX_BYTES);
        assertThat(SiteImagePolicy.decode(b64(limit)).bytes()).hasSize(SiteImagePolicy.MAX_BYTES);
    }

    @Test
    @DisplayName("vazio e data URI sem base64 são recusados")
    void rejectsEmptyAndMalformed() {
        assertThatThrownBy(() -> SiteImagePolicy.decode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteImagePolicy.decode("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteImagePolicy.decode("data:image/jpeg,naoebase64")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteImagePolicy.decode("data:image/jpeg;base64,")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("só a lista fechada é servida")
    void servable() {
        assertThat(SiteImagePolicy.isServable("image/jpeg")).isTrue();
        assertThat(SiteImagePolicy.isServable("image/svg+xml")).isFalse();
        assertThat(SiteImagePolicy.isServable("text/html")).isFalse();
        assertThat(SiteImagePolicy.isServable(null)).isFalse();
    }
}

package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("URLs aceitas no site white-label")
class SiteUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(document.cookie)",
            "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "http://exemplo.com/foto.jpg",
            "//exemplo.com/foto.jpg",
            "/foto.jpg",
            "https://",
            "https://user:pass@exemplo.com/x",
            "https://exemplo.com/a b.jpg"})
    @DisplayName("recusa esquema perigoso, http, relativo e URL malformada")
    void rejectsUnsafe(String url) {
        assertThatThrownBy(() -> SiteUrlPolicy.requireHttps(url, "logo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("https com host passa; vazio é campo opcional")
    void acceptsHttps() {
        assertThatCode(() -> SiteUrlPolicy.requireHttps("https://cdn.exemplo.com/logo.png?v=2", "logo")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireHttps(null, "logo")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireHttps("  ", "logo")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Instagram só do instagram.com")
    void instagramDomain() {
        assertThatCode(() -> SiteUrlPolicy.requireInstagram("https://www.instagram.com/figaro")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireInstagram("https://instagram.com/figaro")).doesNotThrowAnyException();
        assertThatThrownBy(() -> SiteUrlPolicy.requireInstagram("https://instagram.com.golpe.io/figaro"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteUrlPolicy.requireInstagram("https://golpe.io/instagram.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mapa só do Google Maps")
    void mapsDomain() {
        assertThatCode(() -> SiteUrlPolicy.requireMaps("https://maps.app.goo.gl/abc123")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireMaps("https://www.google.com/maps/place/Figaro")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireMaps("https://www.google.com.br/maps?q=x")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireMaps("https://maps.google.com/?q=x")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireMaps("https://goo.gl/maps/abc")).doesNotThrowAnyException();
        assertThatThrownBy(() -> SiteUrlPolicy.requireMaps("https://www.google.com/search?q=x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteUrlPolicy.requireMaps("https://golpe.io/maps"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("foto nossa (/api/site/img/<uuid>) vale em campo de imagem, e só o padrão exato")
    void ownImage() {
        String id = "3f2b8c1e-9a4d-4e2f-8b7a-1c2d3e4f5a6b";
        assertThatCode(() -> SiteUrlPolicy.requireImage("/api/site/img/" + id, "logo")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireImage("https://cdn.exemplo.com/a.jpg", "logo")).doesNotThrowAnyException();
        assertThatCode(() -> SiteUrlPolicy.requireGallery("/api/site/img/" + id + "\nhttps://a.com/1.jpg"))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(SiteUrlPolicy.ownImageId("/api/site/img/" + id)).isPresent();
        for (String bad : new String[] {
                "/api/site/img/" + id + "?x=1",
                "/api/site/img/" + id + "/../../admin",
                "//evil.com/api/site/img/" + id,
                "/api/site/img/nao-e-uuid",
                "/api/site/img/" + id.toUpperCase(),
                "/api/admin/site",
                "/foto.jpg",
                "javascript:/api/site/img/" + id}) {
            assertThatThrownBy(() -> SiteUrlPolicy.requireImage(bad, "logo"))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        // Link clicavel (Instagram, mapa, requireHttps) continua so https.
        assertThatThrownBy(() -> SiteUrlPolicy.requireHttps("/api/site/img/" + id, "logo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("galeria: uma https por linha, no máximo 12")
    void gallery() {
        assertThatCode(() -> SiteUrlPolicy.requireGallery("https://a.com/1.jpg\n\nhttps://a.com/2.jpg\n"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SiteUrlPolicy.requireGallery("https://a.com/1.jpg\njavascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        String treze = String.join("\n", java.util.Collections.nCopies(13, "https://a.com/x.jpg"));
        assertThatThrownBy(() -> SiteUrlPolicy.requireGallery(treze))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
    }
}

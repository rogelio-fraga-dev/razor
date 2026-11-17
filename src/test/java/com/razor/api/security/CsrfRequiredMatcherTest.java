package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Onde o CSRF e exigido ({@link SecurityConfig#CSRF_REQUIRED}). Roda sem Docker: o IT cobre o
 * fluxo real, este fixa a fronteira — errar para menos abre CSRF no painel, errar para mais
 * derruba webhook/onboarding/site.
 */
@DisplayName("Matcher de exigência de CSRF")
class CsrfRequiredMatcherTest {

    @ParameterizedTest(name = "{0} {1} (Authorization={2}) -> exige CSRF? {3}")
    @CsvSource({
            // Escritas do painel por cookie: exigem.
            "POST,   /api/admin/services,       , true",
            "PUT,    /api/admin/me,             , true",
            "PATCH,  /api/admin/comandas/1,     , true",
            "DELETE, /api/admin/coupons/1,      , true",
            "POST,   /api/auth/logout,          , true",
            // Leitura nunca exige.
            "GET,    /api/admin/me,             , false",
            "HEAD,   /api/admin/me,             , false",
            "OPTIONS,/api/admin/services,       , false",
            // Rotas fora do painel: segredo proprio ou publicas.
            "POST,   /api/auth/login,           , false",
            "POST,   /api/webhook,              , false",
            "POST,   /api/webhook/evolution,    , false",
            "POST,   /api/onboarding,           , false",
            "POST,   /api/site/figaro/lead,     , false",
            // Cliente de API com Bearer: o cookie nao e usado, entao nao ha CSRF a defender.
            "POST,   /api/admin/services,       Bearer abc, false",
    })
    void boundary(String method, String path, String authorization, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (authorization != null) request.addHeader("Authorization", authorization);

        assertThat(SecurityConfig.CSRF_REQUIRED.matches(request)).isEqualTo(expected);
    }
}

package com.razor.api.security;

import jakarta.servlet.http.HttpServletRequest;

/** Ponte de teste para o matcher de CSRF (visibilidade de pacote). */
public final class SecurityConfigAccess {

    private SecurityConfigAccess() {
    }

    public static boolean csrfRequired(HttpServletRequest request) {
        return SecurityConfig.CSRF_REQUIRED.matches(request);
    }
}

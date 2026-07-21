package com.razor.api.security;

import com.razor.api.model.UserRole;
import java.util.UUID;

/**
 * Identidade autenticada de um usuario do painel web, extraida do JWT. Vira o principal do
 * {@code SecurityContext} e a fonte do tenant da requisicao.
 */
public record TenantUserPrincipal(UUID userId, String email, UUID barbershopId, UserRole role) {
}

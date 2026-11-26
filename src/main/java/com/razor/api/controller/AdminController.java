package com.razor.api.controller;

import com.razor.api.model.TenantUser;
import com.razor.api.security.SessionCookies;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Identidade do usuario logado no painel (protegido por JWT): {@code GET /me} devolve quem sou,
 * {@code PUT /me} atualiza nome e senha (perfil).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;
    private final SessionCookies sessionCookies;

    /** Payload do PUT /me: nome e/ou troca de senha (nova senha exige a atual). */
    public record UpdateMeRequest(String name, String currentPassword, String newPassword) {
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(@AuthenticationPrincipal TenantUserPrincipal principal) {
        return ResponseEntity.ok(payload(authService.getUser(principal.userId()), principal));
    }

    @PutMapping("/me")
    public ResponseEntity<Object> updateMe(@AuthenticationPrincipal TenantUserPrincipal principal,
                                           @RequestBody UpdateMeRequest req) {
        TenantUser updated = authService.updateProfile(
                principal.userId(), req.name(), req.currentPassword(), req.newPassword());
        var response = ResponseEntity.ok();
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            // A troca de senha revogou todos os tokens (inclusive o desta requisicao); quem
            // trocou continua logado com um cookie novo, as demais sessoes caem.
            response.header(HttpHeaders.SET_COOKIE,
                    sessionCookies.issue(authService.reissueToken(updated)).toString());
        }
        return response.body(payload(updated, principal));
    }

    private Map<String, Object> payload(TenantUser user, TenantUserPrincipal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", principal.userId());
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.put("barbershopId", principal.barbershopId());
        body.put("role", principal.role().name());
        // Vinculo com o cadastro de profissional (V44): o "Meu dia" filtra a agenda por ele.
        body.put("professionalId", user.getProfessionalId());
        // Sessão de suporte aberta pelo console (só leitura): o painel mostra a faixa amarela.
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        body.put("readOnly", attrs != null && Boolean.TRUE.equals(attrs.getAttribute(
                com.razor.api.security.JwtAuthenticationFilter.READ_ONLY_ATTR,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST)));
        return body;
    }
}

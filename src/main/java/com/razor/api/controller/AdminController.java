package com.razor.api.controller;

import com.razor.api.model.TenantUser;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
        return ResponseEntity.ok(payload(updated, principal));
    }

    private Map<String, Object> payload(TenantUser user, TenantUserPrincipal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", principal.userId());
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.put("barbershopId", principal.barbershopId());
        body.put("role", principal.role().name());
        return body;
    }
}

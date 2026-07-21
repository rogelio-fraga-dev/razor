package com.razor.api.controller;

import com.razor.api.security.TenantUserPrincipal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Raiz da API do painel web (protegida por JWT). Por enquanto expoe apenas {@code /me} (identidade
 * do usuario logado) — os endpoints de configuracao e relatorios entram nos proximos increments.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    @GetMapping("/me")
    public ResponseEntity<Object> me(@AuthenticationPrincipal TenantUserPrincipal principal) {
        return ResponseEntity.ok(Map.of(
                "userId", principal.userId(),
                "email", principal.email(),
                "barbershopId", principal.barbershopId(),
                "role", principal.role().name()));
    }
}

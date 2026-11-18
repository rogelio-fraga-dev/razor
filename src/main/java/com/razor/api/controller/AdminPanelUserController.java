package com.razor.api.controller;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.PanelUserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Logins do painel da barbearia ("Equipe & acesso"): lista, cadastro, papel, ativo, senha
 * provisoria e o vinculo de cada um com um profissional (V44). So o dono (cai no fecho
 * {@code /api/admin/**} do SecurityConfig). Nunca devolve hash de senha. As regras (nao mexer
 * em si mesmo, nunca ficar sem dono ativo) vivem no {@link PanelUserService}.
 */
@RestController
@RequestMapping("/api/admin/panel-users")
@RequiredArgsConstructor
public class AdminPanelUserController {

    private final PanelUserService panelUserService;

    public record PanelUserView(UUID id, String name, String email, String role, boolean active,
                                UUID professionalId) {
        static PanelUserView from(TenantUser u) {
            return new PanelUserView(u.getId(), u.getName(), u.getEmail(), u.getRole().name(),
                    u.isActive(), u.getProfessionalId());
        }
    }

    public record LinkRequest(UUID professionalId) {
    }

    public record CreateRequest(String name, String email, UserRole role, String password, UUID professionalId) {
    }

    public record RoleRequest(UserRole role) {
    }

    public record ActiveRequest(Boolean active) {
    }

    public record PasswordRequest(String password) {
    }

    @GetMapping
    public List<PanelUserView> list() {
        return panelUserService.list().stream().map(PanelUserView::from).toList();
    }

    @PostMapping
    public ResponseEntity<PanelUserView> create(@RequestBody CreateRequest req) {
        if (req == null) throw new IllegalArgumentException("Dados do usuário ausentes.");
        TenantUser saved = panelUserService.create(req.name(), req.email(), req.role(), req.password(),
                req.professionalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PanelUserView.from(saved));
    }

    @PutMapping("/{id}/role")
    public PanelUserView changeRole(@AuthenticationPrincipal TenantUserPrincipal principal,
                                    @PathVariable UUID id, @RequestBody RoleRequest req) {
        return PanelUserView.from(panelUserService.changeRole(actor(principal), id, req == null ? null : req.role()));
    }

    @PutMapping("/{id}/active")
    public PanelUserView setActive(@AuthenticationPrincipal TenantUserPrincipal principal,
                                   @PathVariable UUID id, @RequestBody ActiveRequest req) {
        if (req == null || req.active() == null) throw new IllegalArgumentException("Informe se o acesso fica ativo.");
        return PanelUserView.from(panelUserService.setActive(actor(principal), id, req.active()));
    }

    @PutMapping("/{id}/password")
    public PanelUserView resetPassword(@AuthenticationPrincipal TenantUserPrincipal principal,
                                       @PathVariable UUID id, @RequestBody PasswordRequest req) {
        return PanelUserView.from(panelUserService.resetPassword(actor(principal), id,
                req == null ? null : req.password()));
    }

    @PutMapping("/{id}/professional")
    public PanelUserView link(@PathVariable UUID id, @RequestBody LinkRequest req) {
        return PanelUserView.from(panelUserService.linkProfessional(id, req == null ? null : req.professionalId()));
    }

    private static UUID actor(TenantUserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}

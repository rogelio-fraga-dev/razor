package com.razor.api.controller;

import com.razor.api.model.TenantUser;
import com.razor.api.service.PanelUserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Logins do painel da barbearia e o vinculo de cada um com um profissional (V44). So o dono
 * (cai no fecho {@code /api/admin/**} do SecurityConfig). Nunca devolve hash de senha.
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

    @GetMapping
    public List<PanelUserView> list() {
        return panelUserService.list().stream().map(PanelUserView::from).toList();
    }

    @PutMapping("/{id}/professional")
    public PanelUserView link(@PathVariable UUID id, @RequestBody LinkRequest req) {
        return PanelUserView.from(panelUserService.linkProfessional(id, req == null ? null : req.professionalId()));
    }
}

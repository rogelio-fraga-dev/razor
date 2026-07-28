package com.razor.api.controller;

import com.razor.api.dto.AdvanceRequest;
import com.razor.api.dto.AdvanceResponse;
import com.razor.api.model.Professional;
import com.razor.api.model.ProfessionalAdvance;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AuthService;
import com.razor.api.service.ProfessionalAdvanceService;
import com.razor.api.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Vales e adiantamentos do barbeiro (V26) no painel web (tenant vem do JWT). O desconto na
 * comissao acontece na tela de acerto — ver {@code /commission-payments/settlement}.
 */
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/admin/advances")
@RequiredArgsConstructor
public class AdminAdvanceController {

    private final ProfessionalAdvanceService advanceService;
    private final ProfessionalRepository professionalRepository;
    private final AuthService authService;

    @GetMapping
    public List<AdvanceResponse> history() {
        Map<UUID, String> names = professionalNames();
        return advanceService.history().stream()
                .map(a -> AdvanceResponse.from(a, names.get(a.getProfessionalId())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AdvanceResponse> grant(@AuthenticationPrincipal TenantUserPrincipal principal,
                                                 @Valid @RequestBody AdvanceRequest req) {
        ProfessionalAdvance saved = advanceService.grant(
                req.professionalId(), req.amount(), req.date(),
                Boolean.TRUE.equals(req.fromCashRegister()), req.notes(), nameOf(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdvanceResponse.from(saved, professionalNames().get(saved.getProfessionalId())));
    }

    private Map<UUID, String> professionalNames() {
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
    }

    /** Nome de quem liberou o vale; cai no e-mail do token se nao houver nome cadastrado. */
    private String nameOf(TenantUserPrincipal principal) {
        if (principal == null) return null;
        try {
            String name = authService.getUser(principal.userId()).getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception e) {
            // Cai no e-mail do token, mas com rastro: engolir sem log tornaria "usuario sem nome"
            // indistinguivel de "banco fora do ar" num registro de quem mexeu em dinheiro.
            log.warn("[AUTH] Nao foi possivel resolver o nome do usuario {}: {}",
                    principal.userId(), e.toString());
        }
        return principal.email();
    }
}

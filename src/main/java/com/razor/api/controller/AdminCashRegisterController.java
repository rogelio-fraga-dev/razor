package com.razor.api.controller;

import com.razor.api.dto.CashMovementRequest;
import com.razor.api.dto.CashSessionSummary;
import com.razor.api.dto.CloseCashRequest;
import com.razor.api.dto.OpenCashRequest;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AuthService;
import com.razor.api.service.CashRegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Caixa no painel (V18): abertura, movimentos e fechamento. Tenant e usuário vêm do JWT — o
 * usuário é registrado como quem abriu/fechou a sessão.
 */
@RestController
@RequestMapping("/api/admin/cash-register")
@RequiredArgsConstructor
public class AdminCashRegisterController {

    private final CashRegisterService cashRegisterService;
    private final AuthService authService;

    /** Sessão aberta (com movimentos e totais) ou {@code null} se o caixa está fechado. */
    @GetMapping
    public CashSessionSummary current() {
        return cashRegisterService.currentSummary();
    }

    @PostMapping("/open")
    public CashSessionSummary open(@AuthenticationPrincipal TenantUserPrincipal principal,
                                   @Valid @RequestBody OpenCashRequest req) {
        return cashRegisterService.open(req.openingAmount(), principal.userId(), nameOf(principal));
    }

    @PostMapping("/movements")
    public CashSessionSummary addMovement(@Valid @RequestBody CashMovementRequest req) {
        return cashRegisterService.addMovement(req.type(), req.paymentMethod(), req.amount(), req.description());
    }

    @PostMapping("/close")
    public CashSessionSummary close(@AuthenticationPrincipal TenantUserPrincipal principal,
                                    @Valid @RequestBody CloseCashRequest req) {
        return cashRegisterService.close(req.counted(), req.notes(), principal.userId(), nameOf(principal));
    }

    /** Nome do usuário para o snapshot de "aberto/fechado por"; cai no e-mail se não houver nome. */
    private String nameOf(TenantUserPrincipal principal) {
        try {
            String name = authService.getUser(principal.userId()).getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {
            // sem nome resolvido: usa o e-mail do token
        }
        return principal.email();
    }
}

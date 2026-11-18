package com.razor.api.controller;

import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.MyEarningsService;
import com.razor.api.service.MyEarningsService.Earnings;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Quanto eu ganhei" do "Meu dia" (V44): {@code GET /api/admin/me/earnings?period=today|yesterday|
 * week|fortnight|month}. Liberado para todo papel no SecurityConfig, porque o escopo e o proprio
 * usuario — quem decide o que cada papel ve e o {@link MyEarningsService}.
 */
@RestController
@RequestMapping("/api/admin/me/earnings")
@RequiredArgsConstructor
public class AdminMyEarningsController {

    private final MyEarningsService myEarningsService;

    @GetMapping
    public Earnings earnings(@AuthenticationPrincipal TenantUserPrincipal principal,
                             @RequestParam(required = false) String period,
                             @RequestParam(required = false) UUID professionalId) {
        return myEarningsService.earnings(principal, period, professionalId);
    }
}

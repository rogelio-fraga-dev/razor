package com.razor.api.controller;

import com.razor.api.dto.ReviewSettingsPayload;
import com.razor.api.dto.ReviewSummaryResponse;
import com.razor.api.service.BarbershopSettingsService;
import com.razor.api.service.CustomerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Avaliacao pos-atendimento (V46) no painel do dono: o resumo das notas e as regras do pedido
 * (ligado, atraso, link do Google). Sem regra propria no SecurityConfig: so o dono.
 */
@RestController
@RequiredArgsConstructor
public class AdminReviewController {

    private final CustomerReviewService reviewService;
    private final BarbershopSettingsService settingsService;

    @GetMapping("/api/admin/reviews/summary")
    public ReviewSummaryResponse summary(@RequestParam(name = "days", defaultValue = "90") int days) {
        return reviewService.summary(Math.max(1, Math.min(days, 365)));
    }

    @GetMapping("/api/admin/settings/reviews")
    public ReviewSettingsPayload getSettings() {
        return toPayload(settingsService.getReviewSettings());
    }

    @PutMapping("/api/admin/settings/reviews")
    public ReviewSettingsPayload updateSettings(@Valid @RequestBody ReviewSettingsPayload req) {
        return toPayload(settingsService.updateReviewSettings(req));
    }

    private static ReviewSettingsPayload toPayload(BarbershopSettingsService.ReviewSettings s) {
        return new ReviewSettingsPayload(s.enabled(), s.googleReviewUrl(), s.delayMinutes());
    }
}

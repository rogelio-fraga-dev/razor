package com.razor.api.controller;

import com.razor.api.dto.GoalsPayload;
import com.razor.api.service.BarbershopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Metas do dono no painel web (tenant vem do JWT). Endpoint proprio, separado das configuracoes
 * do bot: aquele PUT troca o registro inteiro e apagaria a meta a cada salvamento de texto.
 */
@RestController
@RequestMapping("/api/admin/settings/goals")
@RequiredArgsConstructor
public class AdminGoalsController {

    private final BarbershopSettingsService barbershopSettingsService;

    @GetMapping
    public GoalsPayload get() {
        return new GoalsPayload(barbershopSettingsService.getMonthlyRevenueGoal());
    }

    /** Define a meta do mes. Enviar {@code null} limpa a meta (o painel volta a omitir o card). */
    @PutMapping
    public GoalsPayload update(@Valid @RequestBody GoalsPayload req) {
        return new GoalsPayload(barbershopSettingsService.setMonthlyRevenueGoal(req.monthlyRevenueGoal()));
    }
}

package com.razor.api.controller;

import com.razor.api.dto.NoShowSettingsPayload;
import com.razor.api.service.BarbershopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Anti-falta (V42) no painel do dono: confirmacao da vespera, liberar vaga sem resposta e sinal
 * para quem falta muito. Endpoint proprio pelo mesmo motivo do limite de agendamentos.
 */
@RestController
@RequestMapping("/api/admin/settings/no-show")
@RequiredArgsConstructor
public class AdminNoShowSettingsController {

    private final BarbershopSettingsService barbershopSettingsService;

    @GetMapping
    public NoShowSettingsPayload get() {
        return toPayload(barbershopSettingsService.getNoShowSettings());
    }

    @PutMapping
    public NoShowSettingsPayload update(@Valid @RequestBody NoShowSettingsPayload req) {
        return toPayload(barbershopSettingsService.updateNoShowSettings(req));
    }

    private NoShowSettingsPayload toPayload(BarbershopSettingsService.NoShowSettings s) {
        return new NoShowSettingsPayload(s.confirmationEnabled(), s.confirmationHour(), s.deadlineHours(),
                s.autoRelease(), s.depositAfter(), s.depositAmount(), barbershopSettingsService.resolve().hasPix());
    }
}

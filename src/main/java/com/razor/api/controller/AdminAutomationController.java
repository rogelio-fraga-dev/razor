package com.razor.api.controller;

import com.razor.api.dto.AutomationSettingsPayload;
import com.razor.api.model.AutomationSend;
import com.razor.api.service.AutomationCampaignService;
import com.razor.api.service.AutomationSettingsService;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Aba Automacoes (V47) no painel do dono: lista de espera automatica, campanhas (sumiu,
 * aniversario, retorno) e relatorio semanal. So o dono (regra padrao de /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/settings/automations")
@RequiredArgsConstructor
public class AdminAutomationController {

    private final AutomationSettingsService settingsService;
    private final AutomationCampaignService campaignService;

    @GetMapping
    public AutomationSettingsPayload get() {
        return settingsService.payload();
    }

    @PutMapping
    public AutomationSettingsPayload update(@Valid @RequestBody AutomationSettingsPayload req) {
        return settingsService.update(req);
    }

    /** Previa do texto com um cliente de exemplo — nada e enviado. */
    @PostMapping("/preview")
    public AutomationSettingsPayload.PreviewResponse preview(@Valid @RequestBody AutomationSettingsPayload.PreviewRequest req) {
        AutomationSend.Kind kind;
        try {
            kind = AutomationSend.Kind.valueOf(req.kind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = null;
        }
        if (kind == null || kind == AutomationSend.Kind.WEEKLY_REPORT) {
            throw new IllegalArgumentException("Tipo de campanha inválido: use LAPSED, BIRTHDAY ou RETURN.");
        }
        return new AutomationSettingsPayload.PreviewResponse(campaignService.preview(kind, req.template()));
    }
}

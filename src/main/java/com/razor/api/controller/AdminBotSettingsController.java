package com.razor.api.controller;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.BotSettingsPayload;
import com.razor.api.dto.BotStatusResponse;
import com.razor.api.dto.WhatsAppStatusResponse;
import com.razor.api.service.BarbershopSettingsService;
import com.razor.api.service.WhatsAppConnectionService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Configuracoes do bot (textos/links, liga-desliga e saude) no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/settings/bot")
@RequiredArgsConstructor
public class AdminBotSettingsController {

    private final BarbershopSettingsService barbershopSettingsService;
    private final WhatsAppConnectionService whatsAppConnectionService;
    private final RazorProperties properties;

    @GetMapping
    public BotSettingsPayload get() {
        return BotSettingsPayload.from(barbershopSettingsService.getOrEmpty());
    }

    @PutMapping
    public BotSettingsPayload update(@Valid @RequestBody BotSettingsPayload req) {
        return BotSettingsPayload.from(barbershopSettingsService.update(
                req.addressText(), req.addressMapsUrl(), req.servicesText(), req.scheduleUrl(), req.instagramUrl()));
    }

    /** Saude do bot: WhatsApp conectado? IA configurada? Bot ligado? — a confirmacao de "esta ok". */
    @GetMapping("/status")
    public BotStatusResponse status() {
        WhatsAppStatusResponse wa = whatsAppConnectionService.status();
        return new BotStatusResponse(
                wa.instanceName(), wa.state(), wa.connected(), isAiConfigured(),
                barbershopSettingsService.isBotEnabled());
    }

    /** Liga/desliga o atendimento automatico de clientes deste tenant. */
    @PutMapping("/enabled")
    public Map<String, Boolean> setEnabled(@RequestBody Map<String, Boolean> req) {
        boolean enabled = Boolean.TRUE.equals(req.get("enabled"));
        return Map.of("enabled", barbershopSettingsService.setBotEnabled(enabled));
    }

    private boolean isAiConfigured() {
        String key = properties.getOpenai().getApiKey();
        return key != null && !key.isBlank() && !key.contains("dummy");
    }
}

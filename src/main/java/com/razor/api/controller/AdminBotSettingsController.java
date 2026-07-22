package com.razor.api.controller;

import com.razor.api.dto.BotSettingsPayload;
import com.razor.api.service.BarbershopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Configuracoes do bot (textos/links do WhatsApp) no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/settings/bot")
@RequiredArgsConstructor
public class AdminBotSettingsController {

    private final BarbershopSettingsService barbershopSettingsService;

    @GetMapping
    public BotSettingsPayload get() {
        return BotSettingsPayload.from(barbershopSettingsService.getOrEmpty());
    }

    @PutMapping
    public BotSettingsPayload update(@Valid @RequestBody BotSettingsPayload req) {
        return BotSettingsPayload.from(barbershopSettingsService.update(
                req.addressText(), req.addressMapsUrl(), req.servicesText(), req.scheduleUrl(), req.instagramUrl()));
    }
}

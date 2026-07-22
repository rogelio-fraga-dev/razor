package com.razor.api.dto;

import com.razor.api.model.BarbershopSettings;

/**
 * Textos/links do bot editaveis no painel (endereco, mapa, tabela de servicos, agendamento,
 * Instagram). Todos opcionais — campo vazio faz o bot cair no texto do config global.
 */
public record BotSettingsPayload(
        String addressText,
        String addressMapsUrl,
        String servicesText,
        String scheduleUrl,
        String instagramUrl) {

    public static BotSettingsPayload from(BarbershopSettings s) {
        return new BotSettingsPayload(
                s.getAddressText(), s.getAddressMapsUrl(), s.getServicesText(),
                s.getScheduleUrl(), s.getInstagramUrl());
    }
}

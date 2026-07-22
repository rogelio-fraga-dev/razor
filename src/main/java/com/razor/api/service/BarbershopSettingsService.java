package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.repository.BarbershopSettingsRepository;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Configuracoes do bot por tenant (textos/links do WhatsApp). {@link #resolve()} devolve os
 * valores efetivos — linha do tenant com <b>fallback por campo</b> no config global
 * ({@code razor.menu.*}) —, garantindo que o tenant padrao (sem linha) siga como antes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarbershopSettingsService {

    private final BarbershopSettingsRepository repository;
    private final RazorProperties properties;

    /** Textos efetivos usados no fluxo do cliente. */
    public record BotSettings(
            String addressText, String addressMapsUrl, String servicesText, String scheduleUrl, String instagramUrl) {
    }

    public BotSettings resolve() {
        RazorProperties.Menu menu = properties.getMenu();
        BarbershopSettings s = repository.findById(TenantContext.getCurrent()).orElse(null);
        return new BotSettings(
                pick(s == null ? null : s.getAddressText(), menu.getAddressText()),
                pick(s == null ? null : s.getAddressMapsUrl(), menu.getAddressMapsUrl()),
                pick(s == null ? null : s.getServicesText(), menu.getServicesText()),
                pick(s == null ? null : s.getScheduleUrl(), menu.getScheduleUrl()),
                pick(s == null ? null : s.getInstagramUrl(), menu.getInstagramUrl()));
    }

    /** Linha do tenant para edicao (ou uma vazia, sem persistir, quando ainda nao existe). */
    public BarbershopSettings getOrEmpty() {
        return repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
    }

    public BarbershopSettings update(String addressText, String addressMapsUrl, String servicesText,
                                     String scheduleUrl, String instagramUrl) {
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setAddressText(addressText);
        s.setAddressMapsUrl(addressMapsUrl);
        s.setServicesText(servicesText);
        s.setScheduleUrl(scheduleUrl);
        s.setInstagramUrl(instagramUrl);
        BarbershopSettings saved = repository.save(s);
        log.info("[BOT] Configuracoes do bot atualizadas para o tenant {}", TenantContext.getCurrent());
        return saved;
    }

    private String pick(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}

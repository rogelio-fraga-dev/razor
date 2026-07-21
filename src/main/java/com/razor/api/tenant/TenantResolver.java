package com.razor.api.tenant;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolve qual barbearia (tenant) atende uma mensagem, a partir do nome da instancia
 * da Evolution API que recebeu o webhook. Cada barbearia tem sua propria instancia de
 * WhatsApp (ver {@code barbershops.whatsapp_instance}).
 *
 * <p>Fase 1 — increment 2. Instancia desconhecida (ou ausente) recai no
 * {@link TenantContext#DEFAULT_BARBERSHOP_ID}, preservando o comportamento single-tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantResolver {

    private final BarbershopRepository barbershopRepository;

    public UUID resolveByInstance(String instance) {
        if (instance == null || instance.isBlank()) {
            return TenantContext.DEFAULT_BARBERSHOP_ID;
        }
        return barbershopRepository.findByWhatsappInstance(instance)
                .map(Barbershop::getId)
                .orElseGet(() -> {
                    log.warn("[TENANT] Instancia '{}' sem barbearia correspondente; usando tenant padrao.", instance);
                    return TenantContext.DEFAULT_BARBERSHOP_ID;
                });
    }
}

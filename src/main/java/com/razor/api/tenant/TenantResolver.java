package com.razor.api.tenant;

import com.razor.api.config.RazorProperties;
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
    private final RazorProperties properties;

    /**
     * Tenant dono da instancia, ou {@code null} quando a instancia e desconhecida.
     *
     * <p><b>Sem fallback no tenant padrao</b> (mudanca de seguranca): antes, uma instancia
     * desconhecida caia na barbearia padrao — o que permitia que uma mensagem forjada fosse
     * processada como se fosse dela (inclusive comandos de admin). Instancia nao cadastrada
     * agora e simplesmente descartada pelo {@code WebhookRouter}.
     *
     * <p>A instancia do config global ({@code razor.evolution.instance-name}) continua valendo
     * para o tenant padrao — ela existe no banco desde a V4.
     */
    public UUID resolveByInstance(String instance) {
        if (instance == null || instance.isBlank()) {
            log.warn("[TENANT] Webhook sem instancia — descartado.");
            return null;
        }
        return barbershopRepository.findByWhatsappInstance(instance)
                .map(Barbershop::getId)
                .orElseGet(() -> {
                    log.warn("[TENANT] Instancia '{}' nao cadastrada — mensagem descartada.", instance);
                    return null;
                });
    }

    /**
     * Nome da instancia da Evolution pela qual o tenant ATUAL deve ENVIAR (saida). Usa
     * {@code barbershops.whatsapp_instance}; se ausente/desconhecida, recai na instancia do
     * config global ({@code razor.evolution.instance-name}) — o tenant padrao ja aponta para ela,
     * entao o comportamento single-tenant fica identico.
     *
     * <p><b>Importante:</b> chame na thread que tem o {@link TenantContext} setado. O envio real
     * roda em {@code CompletableFuture.runAsync}, que NAO propaga o ThreadLocal — a instancia
     * precisa ser resolvida <em>antes</em> de ir para o async.
     */
    public String outboundInstance() {
        return barbershopRepository.findById(TenantContext.getCurrent())
                .map(Barbershop::getWhatsappInstance)
                .filter(instance -> instance != null && !instance.isBlank())
                .orElseGet(() -> properties.getEvolution().getInstanceName());
    }
}

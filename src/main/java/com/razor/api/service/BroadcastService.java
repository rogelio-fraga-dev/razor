package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Disparo de aviso/promocao para uma lista de clientes, um por vez, com pausa entre envios.
 *
 * <p>O laco nasceu no {@link AdminCommandService} (disparo pelo WhatsApp do dono) e foi extraido
 * para o painel poder disparar para um grupo (tag/status, V36) pelo <b>mesmo</b> caminho: mesmo
 * cabecalho, mesma pausa anti-bloqueio do WhatsApp, mesmo tratamento de falha por contato.
 */
@Slf4j
@Service
public class BroadcastService {

    private final WhatsAppMessenger messenger;
    private final BarbershopSettingsService barbershopSettingsService;
    /** Pausa entre mensagens: rajada de envios e o jeito mais rapido de ter o numero bloqueado. */
    private final long delayMillis;

    public BroadcastService(WhatsAppMessenger messenger,
                            BarbershopSettingsService barbershopSettingsService,
                            @Value("${razor.broadcast.delay-ms:3000}") long delayMillis) {
        this.messenger = messenger;
        this.barbershopSettingsService = barbershopSettingsService;
        this.delayMillis = delayMillis;
    }

    /** Quem recebeu (nome) e quantos falharam — o relatorio ao dono mostra os dois. */
    public record Resultado(List<String> enviados, int falhas) {
    }

    /**
     * Envia agora, nesta thread. Envio SINCRONO: so conta como alcancado quem a Evolution
     * aceitou — com o envio assincrono o relatorio contava a base inteira, mesmo com o WhatsApp fora.
     *
     * @param skipPhone telefone a pular (o do admin que disparou); pode ser nulo
     */
    public Resultado deliver(List<Customer> recipients, String message, boolean promo, String skipPhone) {
        // Nome resolvido uma vez, fora do laco: nao ha motivo para reler a config a cada contato.
        String barbershopName = barbershopSettingsService.resolve().displayName();
        String header = promo
                ? "💈 *" + barbershopName + " - Promoção* 💈\n\n"
                : "📢 *Aviso da " + barbershopName + "*\n\n";

        List<String> enviados = new ArrayList<>();
        int falhas = 0;
        for (Customer customer : recipients) {
            try {
                if (skipPhone != null && customer.getPhoneNumber().contains(skipPhone)) continue;
                messenger.sendSync(customer.getPhoneNumber(), header + message, "DISPARO");
                enviados.add(customer.getName() != null ? customer.getName() : customer.getPhoneNumber());
                if (delayMillis > 0) Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[DISPARO] Interrompido durante o envio para {}", customer.getPhoneNumber());
                break;
            } catch (Exception e) {
                falhas++;
                log.warn("[DISPARO] Falha ao enviar para {}: {}", customer.getPhoneNumber(), e.getMessage(), e);
            }
        }
        return new Resultado(enviados, falhas);
    }

    /**
     * O mesmo disparo em segundo plano — o painel nao pode segurar a requisicao 3s por contato.
     * A thread do {@code @Async} nao herda o tenant, entao ele vem explicito e e limpo no fim.
     */
    @Async
    public void deliverInBackground(UUID barbershopId, List<Customer> recipients, String message, boolean promo) {
        TenantContext.set(barbershopId);
        try {
            Resultado r = deliver(recipients, message, promo, null);
            log.info("[DISPARO] Disparo do painel concluido no tenant {}: {}/{} enviados, {} falhas",
                    barbershopId, r.enviados().size(), recipients.size(), r.falhas());
        } finally {
            TenantContext.clear();
        }
    }
}

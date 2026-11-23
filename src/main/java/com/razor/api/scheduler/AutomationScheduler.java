package com.razor.api.scheduler;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.service.AutomationCampaignService;
import com.razor.api.service.WaitlistOfferService;
import com.razor.api.service.WeeklyReportService;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relogio das automacoes (V47). Cada job so dispara; as regras (horario comercial, limite de
 * frequencia, idempotencia, dia/hora do relatorio) ficam nos servicos.
 *
 * <ul>
 *   <li>Prazo das ofertas da lista de espera: a cada minuto (o prazo e de minutos).</li>
 *   <li>Relatorio semanal: a cada 15 min confere se chegou o dia/hora de cada barbearia — uma vez
 *       por semana por admin, mesmo com restart.</li>
 *   <li>Campanhas (sumiu, aniversario, retorno): a cada 15 min, em segundo plano no executor de
 *       disparos, ate {@code MAX_PER_RUN} mensagens por barbearia por rodada.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScheduler {

    private final BarbershopRepository barbershopRepository;
    private final WaitlistOfferService waitlistOfferService;
    private final WeeklyReportService weeklyReportService;
    private final AutomationCampaignService campaignService;

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void expireWaitlistOffers() {
        try {
            int n = waitlistOfferService.expireDue(LocalDateTime.now(AppointmentDates.ZONE));
            if (n > 0) log.info("[LISTA ESPERA] {} oferta(s) expirada(s) e passada(s) adiante", n);
        } catch (Exception e) {
            log.error("[LISTA ESPERA] Falha ao expirar ofertas: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 2/15 * * * *", zone = "America/Sao_Paulo")
    public void sendWeeklyReports() {
        LocalDateTime now = LocalDateTime.now(AppointmentDates.ZONE);
        for (Barbershop shop : barbershopRepository.findByActiveTrue()) {
            try {
                TenantContext.set(shop.getId());
                weeklyReportService.sendIfDue(now);
            } catch (Exception e) {
                log.error("[RELATORIO SEMANAL] Erro no tenant {}: {}", shop.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 7/15 * * * *", zone = "America/Sao_Paulo")
    public void runCampaigns() {
        for (Barbershop shop : barbershopRepository.findByActiveTrue()) {
            try {
                campaignService.runInBackground(shop.getId());
            } catch (Exception e) {
                log.error("[AUTOMACOES] Nao consegui agendar a rodada do tenant {}: {}", shop.getId(), e.getMessage(), e);
            }
        }
    }
}

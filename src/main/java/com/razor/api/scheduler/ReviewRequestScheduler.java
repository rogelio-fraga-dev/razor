package com.razor.api.scheduler;

import com.razor.api.model.CustomerReview;
import com.razor.api.repository.CustomerReviewRepository;
import com.razor.api.service.CustomerReviewService;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Motor dos pedidos de avaliacao pos-atendimento (V46). A cada minuto envia os programados que
 * venceram (no maximo {@link #MAX_PER_RUN}, os mais antigos primeiro) e expira as perguntas sem
 * resposta. Separado do {@code RazorScheduler}: a avaliacao tem tabela propria, nao usa
 * {@code scheduled_tasks}. Cada pedido roda com o tenant dele no contexto; a falha de um nao para os
 * outros (e cancela so aquele — nada de reenviar a cada minuto).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestScheduler {

    static final int MAX_PER_RUN = 50;

    private final CustomerReviewRepository repository;
    private final CustomerReviewService reviewService;

    @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    public void run() {
        LocalDateTime now = LocalDateTime.now(AppointmentDates.ZONE);
        List<CustomerReview> due = repository.findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(
                CustomerReview.Status.SCHEDULED, now, PageRequest.of(0, MAX_PER_RUN));
        for (CustomerReview review : due) {
            try {
                TenantContext.set(review.getBarbershopId());
                reviewService.sendOne(review, now);
            } catch (Exception e) {
                log.error("[AVALIACAO] Falha ao enviar o pedido {} — cancelado", review.getId(), e);
                try {
                    review.setStatus(CustomerReview.Status.CANCELLED);
                    repository.save(review);
                } catch (Exception again) {
                    log.error("[AVALIACAO] Nao consegui cancelar o pedido {}", review.getId(), again);
                }
            } finally {
                TenantContext.clear();
            }
        }
        try {
            int expirados = reviewService.expireUnanswered(now);
            if (expirados > 0) log.info("[AVALIACAO] {} pedido(s) sem resposta expirado(s)", expirados);
        } catch (Exception e) {
            log.error("[AVALIACAO] Falha ao expirar pedidos sem resposta", e);
        }
    }
}

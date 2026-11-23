package com.razor.api.scheduler;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.Barbershop;
import com.razor.api.model.IncidentType;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.service.HealthSignals;
import com.razor.api.service.IncidentService;
import com.razor.api.service.WhatsAppConnectionService;
import com.razor.api.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitor de saude (V50): a cada {@code razor.monitoring.interval-ms} olha cada barbearia ativa
 * e a plataforma, e abre ou resolve incidentes no {@link IncidentService}.
 *
 * <ul>
 *   <li><b>WhatsApp</b>: estado da instancia na Evolution. Fora ({@code close}/{@code connecting})
 *       por {@code whatsapp-down-checks} verificacoes seguidas = desconectado (um pisca de
 *       reconexao nao vira alarme). Barbearia que nunca definiu instancia fica de fora.</li>
 *   <li><b>IA</b> e <b>envios</b>: sinais do {@link HealthSignals}. Abre com N falhas seguidas
 *       (cota/chave invalida da IA abre na primeira — nao passa sozinho); resolve com um sucesso
 *       depois da falha, ou quando ficou {@code stale-resolve-hours} sem falha nova.</li>
 *   <li><b>Fila</b>: tarefa pendente (fora disparo em massa) atrasada ha mais de
 *       {@code queue-stuck-minutes}.</li>
 *   <li><b>Plataforma</b>: motor de tarefas sem batimento ha {@code scheduler-stale-minutes};
 *       Evolution sem responder para nenhuma instancia.</li>
 * </ul>
 *
 * <p>Erro numa barbearia e logado e nao interrompe as outras. Roda no pool de agendamento
 * ({@code spring.task.scheduling.pool.size}), que precisa de mais de uma thread: com uma so, um
 * motor de tarefas travado travaria o proprio monitor que deveria denuncia-lo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemHealthMonitor {

    private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");
    private static final String STATE_OPEN = "open";
    private static final String STATE_UNKNOWN = "unknown";

    private final BarbershopRepository barbershopRepository;
    private final ScheduledTaskRepository taskRepository;
    private final WhatsAppConnectionService connectionService;
    private final HealthSignals signals;
    private final IncidentService incidents;
    private final RazorProperties properties;

    /** Ultima falha ja contada por (barbearia, canal): so falha NOVA soma no incidente. */
    private final Map<String, Instant> seenFailure = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${razor.monitoring.interval-ms:120000}",
            initialDelayString = "${razor.monitoring.initial-delay-ms:60000}")
    public void run() {
        if (!properties.getMonitoring().isEnabled()) return;
        try {
            check();
        } catch (Exception e) {
            log.error("[MONITOR] Falha na verificacao de saude", e);
        }
    }

    /** Uma rodada completa (publica para teste). */
    public void check() {
        RazorProperties.Monitoring cfg = properties.getMonitoring();
        boolean schedulerStalled = checkScheduler(cfg);

        int checked = 0;
        int unknown = 0;
        for (Barbershop shop : barbershopRepository.findByActiveTrue()) {
            try {
                TenantContext.set(shop.getId());
                String state = checkWhatsApp(shop, cfg);
                if (state != null) {
                    checked++;
                    if (STATE_UNKNOWN.equals(state)) unknown++;
                }
                checkChannel(shop.getId(), "ai", signals.ai(shop.getId()), IncidentType.AI_FAILURE,
                        cfg.getAiFailureThreshold(), true, cfg);
                checkChannel(shop.getId(), "send", signals.send(shop.getId()), IncidentType.SEND_FAILURE,
                        cfg.getSendFailureThreshold(), false, cfg);
                // Com o motor parado o problema e da plataforma (ja tem incidente proprio): nao
                // vale abrir "fila parada" em cada barbearia e avisar todos os donos.
                if (!schedulerStalled) checkQueue(shop.getId(), cfg);
            } catch (Exception e) {
                log.error("[MONITOR] Erro ao verificar o tenant {}: {}", shop.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (checked > 0 && unknown == checked) {
            incidents.raise(null, IncidentType.EVOLUTION_UNAVAILABLE, "nenhuma instância respondeu ao monitor");
        } else if (checked > 0) {
            incidents.resolve(null, IncidentType.EVOLUTION_UNAVAILABLE);
        }
    }

    /** @return true se o motor de tarefas esta parado. */
    private boolean checkScheduler(RazorProperties.Monitoring cfg) {
        Duration age = Duration.between(signals.lastSchedulerBeat(), signals.now());
        if (age.toMinutes() >= cfg.getSchedulerStaleMinutes()) {
            incidents.raise(null, IncidentType.SCHEDULER_STALLED, "sem rodar há " + age.toMinutes() + " min");
            return true;
        }
        incidents.resolve(null, IncidentType.SCHEDULER_STALLED);
        return false;
    }

    /** @return o estado lido, ou null se a barbearia nao tem instancia definida. */
    private String checkWhatsApp(Barbershop shop, RazorProperties.Monitoring cfg) {
        String instance = shop.getWhatsappInstance();
        if (instance == null || instance.isBlank()) return null;
        String state = connectionService.stateOf(instance);
        if (STATE_OPEN.equals(state)) {
            signals.whatsappUp(shop.getId());
            incidents.resolve(shop.getId(), IncidentType.WHATSAPP_DISCONNECTED);
        } else if (!STATE_UNKNOWN.equals(state)) {
            int downChecks = signals.whatsappDownCheck(shop.getId());
            if (downChecks >= cfg.getWhatsappDownChecks()) {
                // So a primeira vez conta como ocorrencia nova; depois so mantem aberto.
                if (!incidents.isOpen(shop.getId(), IncidentType.WHATSAPP_DISCONNECTED)) {
                    incidents.raise(shop.getId(), IncidentType.WHATSAPP_DISCONNECTED,
                            "connecting".equals(state) ? "aguardando leitura do QR" : "sessão encerrada — precisa ler o QR");
                }
            }
        }
        // "unknown" = a Evolution nao respondeu: nada muda aqui (vira incidente de plataforma).
        return state;
    }

    private void checkChannel(UUID shop, String channel, HealthSignals.ChannelState st, IncidentType type,
                              int threshold, boolean fatalKinds, RazorProperties.Monitoring cfg) {
        String key = shop + ":" + channel;
        boolean fatal = fatalKinds && st.consecutiveFailures() >= 1
                && Set.of(HealthSignals.AI_QUOTA, HealthSignals.AI_AUTH).contains(st.lastErrorKind());
        if (st.consecutiveFailures() >= threshold || fatal) {
            Instant last = st.lastFailureAt();
            if (last != null && !last.equals(seenFailure.get(key))) {
                seenFailure.put(key, last);
                incidents.raise(shop, type, st.lastErrorKind() + " (" + st.consecutiveFailures() + " falha(s) seguida(s))");
            }
            return;
        }
        if (st.recovered()) {
            incidents.resolve(shop, type);
            return;
        }
        // Sem trafego depois da falha (ex.: reiniciou ou ninguem mandou mensagem): da como
        // resolvido depois de um tempo sem falha nova, para o painel nao ficar vermelho para sempre.
        Instant last = st.lastFailureAt();
        if (last == null || Duration.between(last, signals.now()).toHours() >= cfg.getStaleResolveHours()) {
            if (incidents.isOpen(shop, type) && staleIncident(shop, type, cfg)) {
                incidents.resolve(shop, type);
            }
        }
    }

    /** Aberto ha mais que o prazo sem ocorrencia nova (vale apos restart, quando o sinal zerou). */
    private boolean staleIncident(UUID shop, IncidentType type, RazorProperties.Monitoring cfg) {
        return incidents.openIncidents().stream()
                .filter(i -> type == i.getType() && shop.equals(i.getBarbershopId()))
                .anyMatch(i -> i.getLastSeenAt().isBefore(LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE).minusHours(cfg.getStaleResolveHours())));
    }

    private void checkQueue(UUID shop, RazorProperties.Monitoring cfg) {
        LocalDateTime cutoff = LocalDateTime.now(BRT).minusMinutes(cfg.getQueueStuckMinutes());
        long late = taskRepository.countByBarbershopIdAndStatusAndTaskTypeNotAndExecutionTimeBefore(
                shop, ScheduledTask.TaskStatus.PENDING, RazorScheduler.BROADCAST_TYPE, cutoff);
        if (late > 0) {
            if (!incidents.isOpen(shop, IncidentType.QUEUE_STUCK)) {
                incidents.raise(shop, IncidentType.QUEUE_STUCK, late + " tarefa(s) atrasada(s) há mais de "
                        + cfg.getQueueStuckMinutes() + " min");
            }
        } else {
            incidents.resolve(shop, IncidentType.QUEUE_STUCK);
        }
    }
}

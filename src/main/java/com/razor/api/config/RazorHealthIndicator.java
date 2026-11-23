package com.razor.api.config;

import com.razor.api.model.SystemIncident;
import com.razor.api.service.HealthSignals;
import com.razor.api.service.IncidentService;
import com.razor.api.service.storage.ImageStorageRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Componente {@code razor} do {@code /actuator/health} (V50).
 *
 * <p>Fica sempre UP: o healthcheck do compose reinicia o container quando a saude cai, e um
 * WhatsApp desconectado numa barbearia nao se resolve reiniciando a API. O que importa vai nos
 * detalhes — que so aparecem com {@code RAZOR_HEALTH_DETAILS=always} (padrao: never) e mesmo
 * assim nao carregam segredo nem dado de barbearia: so contagens por tipo, a idade do batimento
 * do motor de tarefas e o tipo de armazenamento das fotos.
 */
@Component("razor")
@RequiredArgsConstructor
public class RazorHealthIndicator implements HealthIndicator {

    private final HealthSignals signals;
    private final IncidentService incidents;
    private final ImageStorageRegistry storage;
    private final RazorProperties properties;

    @Override
    public Health health() {
        Map<String, Long> open = new TreeMap<>();
        try {
            for (SystemIncident i : incidents.openIncidents()) {
                open.merge(i.getType().name(), 1L, Long::sum);
            }
        } catch (Exception e) {
            // Banco fora: o componente "db" ja denuncia; aqui so nao derruba o health inteiro.
            open.put("indisponivel", -1L);
        }
        long beatAge = Duration.between(signals.lastSchedulerBeat(), signals.now()).getSeconds();
        return Health.up()
                .withDetail("monitoring", properties.getMonitoring().isEnabled())
                .withDetail("openIncidents", open)
                .withDetail("schedulerLastBeatSecondsAgo", beatAge)
                .withDetail("imageStorage", storage.active().backend())
                .build();
    }
}

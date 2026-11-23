package com.razor.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.Barbershop;
import com.razor.api.model.IncidentType;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.service.HealthSignals;
import com.razor.api.service.IncidentService;
import com.razor.api.service.WhatsAppConnectionService;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemHealthMonitorTest {

    private final BarbershopRepository shops = mock(BarbershopRepository.class);
    private final ScheduledTaskRepository tasks = mock(ScheduledTaskRepository.class);
    private final WhatsAppConnectionService conn = mock(WhatsAppConnectionService.class);
    private final IncidentService incidents = mock(IncidentService.class);
    private final RazorProperties props = new RazorProperties();
    private final HealthSignals signals = new HealthSignals();
    private final UUID id = UUID.randomUUID();
    private SystemHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        Barbershop shop = mock(Barbershop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getWhatsappInstance()).thenReturn("inst-1");
        when(shops.findByActiveTrue()).thenReturn(List.of(shop));
        when(conn.stateOf("inst-1")).thenReturn("open");
        signals.schedulerBeat();
        monitor = new SystemHealthMonitor(shops, tasks, conn, signals, incidents, props);
    }

    @Test
    @DisplayName("WhatsApp fora so vira incidente na 2a verificacao seguida; volta = resolve")
    void whatsappDown_needsConsecutiveChecks() {
        when(conn.stateOf("inst-1")).thenReturn("close");
        monitor.check();
        verify(incidents, never()).raise(eq(id), eq(IncidentType.WHATSAPP_DISCONNECTED), anyString());
        monitor.check();
        verify(incidents).raise(eq(id), eq(IncidentType.WHATSAPP_DISCONNECTED), anyString());

        when(conn.stateOf("inst-1")).thenReturn("open");
        monitor.check();
        verify(incidents).resolve(id, IncidentType.WHATSAPP_DISCONNECTED);
    }

    @Test
    @DisplayName("Evolution sem responder para todas as instancias = incidente de plataforma")
    void evolutionUnavailable() {
        when(conn.stateOf("inst-1")).thenReturn("unknown");
        monitor.check();
        verify(incidents).raise(isNull(), eq(IncidentType.EVOLUTION_UNAVAILABLE), anyString());
        verify(incidents, never()).raise(eq(id), eq(IncidentType.WHATSAPP_DISCONNECTED), anyString());
    }

    @Test
    @DisplayName("IA: 3 falhas comuns abrem; cota/chave abre na primeira; a mesma falha nao conta duas vezes")
    void aiFailures() {
        TenantContext.set(id);
        try {
            inTenant(() -> signals.aiFailure(new RuntimeException("timeout")));
            inTenant(() -> signals.aiFailure(new RuntimeException("boom")));
            monitor.check();
            verify(incidents, never()).raise(eq(id), eq(IncidentType.AI_FAILURE), anyString());
            inTenant(() -> signals.aiFailure(new RuntimeException("boom")));
            monitor.check();
            monitor.check(); // sem falha nova: nao soma
            verify(incidents, times(1)).raise(eq(id), eq(IncidentType.AI_FAILURE), anyString());

            inTenant(signals::aiSuccess);
            monitor.check();
            verify(incidents).resolve(id, IncidentType.AI_FAILURE);

            inTenant(() -> signals.aiFailure(new RuntimeException("HTTP 429 insufficient_quota")));
            monitor.check();
            verify(incidents, times(2)).raise(eq(id), eq(IncidentType.AI_FAILURE), anyString());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("fila com tarefa atrasada abre incidente; motor parado vira incidente de plataforma e nao de cada casa")
    void queueAndScheduler() {
        when(tasks.countByBarbershopIdAndStatusAndTaskTypeNotAndExecutionTimeBefore(any(), any(), any(), any()))
                .thenReturn(2L);
        monitor.check();
        verify(incidents).raise(eq(id), eq(IncidentType.QUEUE_STUCK), anyString());

        // batimento ha 10 min: relogio que anda para a frente depois do boot
        var clock = new MutableClock();
        var parado = new HealthSignals(clock);
        parado.schedulerBeat();
        clock.advance(java.time.Duration.ofMinutes(10));
        var m2 = new SystemHealthMonitor(shops, tasks, conn, parado, incidents, props);
        org.mockito.Mockito.clearInvocations(incidents);
        m2.check();
        verify(incidents).raise(isNull(), eq(IncidentType.SCHEDULER_STALLED), anyString());
        verify(incidents, never()).raise(eq(id), eq(IncidentType.QUEUE_STUCK), anyString());
    }

    private void inTenant(Runnable r) {
        TenantContext.set(id);
        try {
            r.run();
        } finally {
            TenantContext.clear();
        }
    }

    static final class MutableClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.now();

        void advance(java.time.Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }
}

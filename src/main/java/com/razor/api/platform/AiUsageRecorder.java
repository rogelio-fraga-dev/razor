package com.razor.api.platform;

import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Uso da IA por barbearia (V51): chamadas, falhas e tokens somados em memoria e gravados em lote
 * em {@code ai_usage_daily} a cada minuto (upsert) — nunca um INSERT por mensagem. Chamada sem
 * tenant (ex.: comando de admin fora de contexto) nao e contada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageRecorder {

    public enum Kind { CHAT, TRANSCRIBE, VISION }

    record Key(UUID shop, LocalDate day, String model, Kind kind) {
    }

    /** [calls, failures, inputTokens, outputTokens] */
    private final Map<Key, AtomicLongArray> pending = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public void success(Kind kind, String model, Integer inputTokens, Integer outputTokens) {
        add(kind, model, 0, inputTokens == null ? 0 : inputTokens, outputTokens == null ? 0 : outputTokens);
    }

    public void failure(Kind kind, String model) {
        add(kind, model, 1, 0, 0);
    }

    private void add(Kind kind, String model, int failed, long in, long out) {
        if (!TenantContext.isSet()) return;
        Key k = new Key(TenantContext.getCurrent(), LocalDate.now(AppointmentDates.ZONE),
                model == null || model.isBlank() ? "?" : (model.length() > 60 ? model.substring(0, 60) : model), kind);
        AtomicLongArray a = pending.computeIfAbsent(k, x -> new AtomicLongArray(4));
        a.incrementAndGet(0);
        if (failed > 0) a.addAndGet(1, failed);
        a.addAndGet(2, in);
        a.addAndGet(3, out);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @PreDestroy
    public void flush() {
        if (pending.isEmpty()) return;
        List<Object[]> batch = new ArrayList<>();
        for (Key k : new ArrayList<>(pending.keySet())) {
            AtomicLongArray a = pending.remove(k);
            if (a == null) continue;
            batch.add(new Object[] {k.shop(), Date.valueOf(k.day()), k.model(), k.kind().name(),
                    a.get(0), a.get(1), a.get(2), a.get(3)});
        }
        try {
            jdbc.batchUpdate("""
                    insert into ai_usage_daily (barbershop_id, day, model, kind, calls, failures, input_tokens, output_tokens)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (barbershop_id, day, model, kind) do update set
                      calls = ai_usage_daily.calls + excluded.calls,
                      failures = ai_usage_daily.failures + excluded.failures,
                      input_tokens = ai_usage_daily.input_tokens + excluded.input_tokens,
                      output_tokens = ai_usage_daily.output_tokens + excluded.output_tokens""", batch);
        } catch (Exception e) {
            // Perder um minuto de contagem e aceitavel; derrubar o agendador nao.
            log.warn("[IA] Nao gravei o uso da IA ({} linha(s)): {}", batch.size(), e.getMessage());
        }
    }
}

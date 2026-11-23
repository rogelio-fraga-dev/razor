package com.razor.api.platform;

import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * O que deu errado numa conversa (V51), para o filtro do console: IA fora (o cliente recebeu a
 * contingencia, que nao fica no historico) e agendamento que a IA prometeu mas nao foi salvo.
 * Nunca derruba o atendimento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationEventRecorder {

    public enum Kind { AI_ERROR, BOOKING_FAILED }

    private final JdbcTemplate jdbc;

    public void record(String phone, Kind kind, String detail) {
        if (!TenantContext.isSet() || phone == null) return;
        try {
            jdbc.update("insert into conversation_events (id, barbershop_id, customer_phone, kind, detail, created_at) "
                            + "values (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), TenantContext.getCurrent(), phone.length() > 20 ? phone.substring(0, 20) : phone,
                    kind.name(), detail == null ? null : (detail.length() > 200 ? detail.substring(0, 200) : detail),
                    Timestamp.valueOf(LocalDateTime.now(AppointmentDates.ZONE)));
        } catch (Exception e) {
            log.warn("[CONSOLE] Evento de conversa nao gravado: {}", e.getMessage());
        }
    }
}

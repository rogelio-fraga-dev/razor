package com.razor.api.platform;

import com.razor.api.util.AppointmentDates;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Trilha do console (V51): login, leitura de conversa (LGPD — quem viu os dados de qual cliente) e
 * mudanca na configuracao da IA. Falha ao gravar so e logada: a trilha nao pode derrubar a acao,
 * mas tambem nao some em silencio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAuditLog {

    private final JdbcTemplate jdbc;

    public record Entry(UUID id, String userEmail, String action, UUID barbershopId, String barbershopName,
                        String target, String ip, LocalDateTime createdAt) {
    }

    public void record(UUID userId, String action, UUID barbershopId, String target, String ip) {
        try {
            jdbc.update("insert into platform_access_log (id, platform_user_id, action, barbershop_id, target, ip, created_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, action, barbershopId, target, ip,
                    Timestamp.valueOf(LocalDateTime.now(AppointmentDates.ZONE)));
        } catch (Exception e) {
            log.error("[PLATAFORMA] Nao gravei a trilha {} de {}: {}", action, userId, e.getMessage());
        }
    }

    public List<Entry> recent(int limit) {
        return jdbc.query("""
                select l.id, u.email, l.action, l.barbershop_id, b.name, l.target, l.ip, l.created_at
                  from platform_access_log l
                  left join platform_users u on u.id = l.platform_user_id
                  left join barbershops b on b.id = l.barbershop_id
                 order by l.created_at desc limit ?""",
                (rs, i) -> new Entry(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getTimestamp(8).toLocalDateTime()),
                limit);
    }
}

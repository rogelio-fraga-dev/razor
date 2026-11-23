package com.razor.api.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sessoes, aparelhos lembrados e codigos de recuperacao do console (V56). Tokens e codigos so como
 * hash SHA-256 no banco: quem ler o banco nao consegue entrar com eles.
 */
@Component
@RequiredArgsConstructor
public class PlatformSecurityStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    static final int RECOVERY_CODES = 10;

    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ sessoes

    public record SessionRow(UUID id, LocalDateTime createdAt, LocalDateTime lastSeenAt, LocalDateTime expiresAt,
                             String ip, String userAgent, String method, LocalDateTime revokedAt) {
    }

    public UUID createSession(UUID userId, LocalDateTime now, LocalDateTime expiresAt, String ip, String userAgent,
                              String method) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into platform_sessions (id, user_id, created_at, last_seen_at, expires_at, ip, user_agent, method) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, ts(now), ts(now), ts(expiresAt), trim(ip, 64), trim(userAgent, 200), method);
        return id;
    }

    public Optional<SessionRow> session(UUID id) {
        return jdbc.query("select id, created_at, last_seen_at, expires_at, ip, user_agent, method, revoked_at "
                + "from platform_sessions where id = ?", (rs, i) -> row(rs), id).stream().findFirst();
    }

    /** Atualiza o "ultimo uso" — chamado no maximo uma vez por minuto por sessao. */
    public void touch(UUID id, LocalDateTime now) {
        jdbc.update("update platform_sessions set last_seen_at = ? where id = ?", ts(now), id);
    }

    public void revokeSession(UUID id, LocalDateTime now) {
        jdbc.update("update platform_sessions set revoked_at = ? where id = ? and revoked_at is null", ts(now), id);
    }

    public void revokeAllSessions(UUID userId, LocalDateTime now) {
        jdbc.update("update platform_sessions set revoked_at = ? where user_id = ? and revoked_at is null", ts(now), userId);
    }

    public List<SessionRow> recentSessions(UUID userId, int limit) {
        return jdbc.query("select id, created_at, last_seen_at, expires_at, ip, user_agent, method, revoked_at "
                + "from platform_sessions where user_id = ? order by created_at desc limit ?", (rs, i) -> row(rs), userId, limit);
    }

    private static SessionRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp r = rs.getTimestamp(8);
        return new SessionRow(rs.getObject(1, UUID.class), rs.getTimestamp(2).toLocalDateTime(),
                rs.getTimestamp(3).toLocalDateTime(), rs.getTimestamp(4).toLocalDateTime(), rs.getString(5),
                rs.getString(6), rs.getString(7), r == null ? null : r.toLocalDateTime());
    }

    // ------------------------------------------------------------------ aparelhos lembrados

    public record DeviceRow(UUID id, LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime lastUsedAt,
                            String ip, String userAgent) {
    }

    /** Cria e devolve o token em claro (vai so para o cookie). */
    public String createTrustedDevice(UUID userId, LocalDateTime now, LocalDateTime expiresAt, String ip, String userAgent) {
        String token = randomToken();
        jdbc.update("insert into platform_trusted_devices (id, user_id, token_hash, created_at, expires_at, ip, user_agent) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, sha256(token), ts(now), ts(expiresAt), trim(ip, 64), trim(userAgent, 200));
        return token;
    }

    /** O aparelho e valido para este usuario agora? Marca o uso. */
    public boolean useTrustedDevice(UUID userId, String token, LocalDateTime now) {
        if (token == null || token.isBlank()) return false;
        return jdbc.update("update platform_trusted_devices set last_used_at = ? where user_id = ? and token_hash = ? "
                + "and revoked_at is null and expires_at > ?", ts(now), userId, sha256(token), ts(now)) > 0;
    }

    public List<DeviceRow> devices(UUID userId, LocalDateTime now) {
        return jdbc.query("select id, created_at, expires_at, last_used_at, ip, user_agent from platform_trusted_devices "
                        + "where user_id = ? and revoked_at is null and expires_at > ? order by created_at desc",
                (rs, i) -> {
                    Timestamp lu = rs.getTimestamp(4);
                    return new DeviceRow(rs.getObject(1, UUID.class), rs.getTimestamp(2).toLocalDateTime(),
                            rs.getTimestamp(3).toLocalDateTime(), lu == null ? null : lu.toLocalDateTime(),
                            rs.getString(5), rs.getString(6));
                }, userId, ts(now));
    }

    public boolean revokeDevice(UUID userId, UUID deviceId, LocalDateTime now) {
        return jdbc.update("update platform_trusted_devices set revoked_at = ? where id = ? and user_id = ? and revoked_at is null",
                ts(now), deviceId, userId) > 0;
    }

    public void revokeAllDevices(UUID userId, LocalDateTime now) {
        jdbc.update("update platform_trusted_devices set revoked_at = ? where user_id = ? and revoked_at is null", ts(now), userId);
    }

    // ------------------------------------------------------------------ codigos de recuperacao

    /** Troca todos os codigos por {@value #RECOVERY_CODES} novos; devolve-os em claro (mostrados uma vez). */
    public List<String> regenerateRecoveryCodes(UUID userId, LocalDateTime now) {
        jdbc.update("delete from platform_recovery_codes where user_id = ?", userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String c = randomCode(4) + "-" + randomCode(4) + "-" + randomCode(4);
            codes.add(c);
            jdbc.update("insert into platform_recovery_codes (id, user_id, code_hash, created_at) values (?, ?, ?, ?)",
                    UUID.randomUUID(), userId, sha256(normalizeCode(c)), ts(now));
        }
        return codes;
    }

    /** Usa um codigo de recuperacao (uma vez so). */
    public boolean useRecoveryCode(UUID userId, String code, LocalDateTime now) {
        if (code == null || code.isBlank()) return false;
        return jdbc.update("update platform_recovery_codes set used_at = ? where id = (select id from platform_recovery_codes "
                + "where user_id = ? and code_hash = ? and used_at is null limit 1)", ts(now), userId, sha256(normalizeCode(code))) > 0;
    }

    public int recoveryCodesLeft(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from platform_recovery_codes where user_id = ? and used_at is null",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void deleteRecoveryCodes(UUID userId) {
        jdbc.update("delete from platform_recovery_codes where user_id = ?", userId);
    }

    /** "ABCD-efgh ijkl" e "abcdefghijkl" valem o mesmo. */
    static String normalizeCode(String code) {
        return code.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ util

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        return sb.toString();
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}

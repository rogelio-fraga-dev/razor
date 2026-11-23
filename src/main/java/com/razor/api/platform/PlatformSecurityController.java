package com.razor.api.platform;

import com.razor.api.security.ClientIp;
import com.razor.api.security.LoginRateLimiter;
import com.razor.api.util.AppointmentDates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Aba Seguranca do console (V56): situacao do 2FA, configurar/desativar, codigos de recuperacao,
 * sessoes recentes, aparelhos lembrados e "sair de todos os aparelhos". Toda acao que muda o 2FA
 * pede um codigo do app (com limite de tentativas) e entra na trilha.
 */
@RestController
@RequestMapping("/api/platform/security")
@RequiredArgsConstructor
public class PlatformSecurityController {

    private final PlatformAuthService authService;
    private final PlatformSecurityStore store;
    private final PlatformSecurity.Cookies cookies;
    private final LoginRateLimiter rateLimiter;
    private final PlatformAuditLog audit;

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record StartRequest(boolean regenerate) {
    }

    public record SessionView(UUID id, boolean current, LocalDateTime createdAt, LocalDateTime lastSeenAt,
                              String ip, String device, String method, boolean active) {
    }

    @GetMapping
    public Map<String, Object> overview(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        PlatformUser u = authService.userOf(p.userId());
        LocalDateTime now = LocalDateTime.now(AppointmentDates.ZONE);
        List<SessionView> sessions = store.recentSessions(u.getId(), 10).stream()
                .map(s -> new SessionView(s.id(), s.id().equals(p.sessionId()), s.createdAt(), s.lastSeenAt(), s.ip(),
                        device(s.userAgent()), s.method(), s.revokedAt() == null && s.expiresAt().isAfter(now)))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", authService.status(u));
        out.put("sessions", sessions);
        out.put("devices", store.devices(u.getId(), now).stream().map(d -> Map.of(
                "id", d.id(), "createdAt", d.createdAt(), "expiresAt", d.expiresAt(),
                "lastUsedAt", d.lastUsedAt() == null ? "" : d.lastUsedAt().toString(),
                "device", device(d.userAgent()), "ip", d.ip() == null ? "" : d.ip())).toList());
        return out;
    }

    @PostMapping("/2fa/setup/start")
    public PlatformAuthService.SetupInfo setupStart(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                                    @RequestBody(required = false) StartRequest req) {
        return authService.setupStart(authService.userOf(p.userId()), req != null && req.regenerate());
    }

    @PostMapping("/2fa/setup/confirm")
    public ResponseEntity<Object> setupConfirm(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                               @Valid @RequestBody CodeRequest req, HttpServletRequest http) {
        return withCode(p, http, "MFA_ENABLED", u -> Map.of("recoveryCodes", authService.setupConfirm(u, req.code())));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Object> disable(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                          @Valid @RequestBody CodeRequest req, HttpServletRequest http) {
        return withCode(p, http, "MFA_DISABLED", u -> {
            authService.disable(u, req.code());
            return Map.of("ok", true);
        });
    }

    @PostMapping("/recovery-codes")
    public ResponseEntity<Object> recoveryCodes(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                                @Valid @RequestBody CodeRequest req, HttpServletRequest http) {
        return withCode(p, http, "RECOVERY_CODES", u -> Map.of("recoveryCodes", authService.regenerateRecoveryCodes(u, req.code())));
    }

    /** Sai de todos os aparelhos (inclusive este) e esquece os aparelhos lembrados. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        authService.logoutAll(authService.userOf(p.userId()));
        audit.record(p.userId(), "LOGOUT_ALL", null, null, ClientIp.of(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearDevice().toString())
                .build();
    }

    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, @PathVariable UUID id) {
        boolean mine = store.recentSessions(p.userId(), 50).stream().anyMatch(s -> s.id().equals(id));
        if (!mine) throw new java.util.NoSuchElementException();
        store.revokeSession(id, LocalDateTime.now(AppointmentDates.ZONE));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> revokeDevice(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, @PathVariable UUID id,
                                             HttpServletRequest http) {
        if (!store.revokeDevice(p.userId(), id, LocalDateTime.now(AppointmentDates.ZONE))) throw new java.util.NoSuchElementException();
        audit.record(p.userId(), "DEVICE_REVOKED", null, null, ClientIp.of(http));
        return ResponseEntity.noContent().build();
    }

    /** Acao que pede um codigo do app: limite de tentativas por usuario e registro na trilha. */
    private ResponseEntity<Object> withCode(PlatformSecurity.PlatformPrincipal p, HttpServletRequest http, String action,
                                            java.util.function.Function<PlatformUser, Map<String, Object>> fn) {
        String key = "platform-code:" + p.userId();
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Muitas tentativas. Tente de novo em " + rateLimiter.lockMinutes() + " minutos."));
        }
        try {
            Map<String, Object> body = fn.apply(authService.userOf(p.userId()));
            rateLimiter.reset(key, ip);
            audit.record(p.userId(), action, null, null, ip);
            return ResponseEntity.ok(body);
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", PlatformAuthController.WRONG_CODE));
        }
    }

    /** "Chrome no Windows", "Safari no iPhone"... — so para reconhecer o aparelho na lista. */
    static String device(String ua) {
        if (ua == null || ua.isBlank()) return "aparelho desconhecido";
        String b = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : ua.contains("curl/") ? "curl" : "navegador";
        String os = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad" : ua.contains("Android") ? "Android"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : "";
        return os.isEmpty() ? b : b + " no " + os;
    }
}

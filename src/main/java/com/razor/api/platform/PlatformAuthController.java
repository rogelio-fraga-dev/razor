package com.razor.api.platform;

import com.razor.api.security.ClientIp;
import com.razor.api.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Login do console da plataforma (V51/V56). A sessao sai so no cookie HttpOnly.
 *
 * <ul>
 *   <li>{@code POST /auth/login}: e-mail + senha → {@code DONE} (sessao), {@code MFA} (falta o codigo)
 *       ou {@code SETUP_REQUIRED} (carencia do 2FA vencida).</li>
 *   <li>{@code POST /auth/verify}: codigo do app ou de recuperacao; "lembrar este aparelho".</li>
 *   <li>{@code POST /auth/setup/start} e {@code /auth/setup/confirm}: configurar o 2FA obrigatorio
 *       ainda sem sessao (com o desafio do login).</li>
 * </ul>
 * Forca bruta: o mesmo {@link LoginRateLimiter} do painel, com chave propria.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformAuthController {

    static final String WRONG_CODE = "Código não confere. Digite os 6 números que o app mostra agora (eles mudam a "
            + "cada 30 segundos). Se continuar, confira se o relógio do celular está em automático.";

    private final PlatformAuthService authService;
    private final PlatformSecurity.Cookies cookies;
    private final LoginRateLimiter rateLimiter;
    private final PlatformAuditLog audit;

    public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 1000) String challenge, @Size(max = 20) String code,
                                @Size(max = 40) String recoveryCode, boolean rememberDevice) {
    }

    public record SetupStartRequest(@NotBlank @Size(max = 1000) String challenge, boolean regenerate) {
    }

    public record SetupConfirmRequest(@NotBlank @Size(max = 1000) String challenge, @NotBlank @Size(max = 20) String code) {
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String key = "platform:" + req.email().trim().toLowerCase();
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) return tooMany();
        try {
            var r = authService.login(req.email(), req.password(), client(http));
            rateLimiter.reset(key, ip);
            if (r.step() == PlatformAuthService.Step.DONE) return sessionResponse(r.session(), ip, null);
            return ResponseEntity.ok(Map.of("step", r.step(), "challenge", r.challenge()));
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "E-mail ou senha incorretos."));
        }
    }

    @PostMapping("/auth/verify")
    public ResponseEntity<Object> verify(@Valid @RequestBody VerifyRequest req, HttpServletRequest http) {
        String key = "platform-mfa:" + Integer.toHexString(req.challenge().hashCode());
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) return tooMany();
        try {
            var s = authService.verify(req.challenge(), req.code(), req.recoveryCode(), req.rememberDevice(), client(http));
            rateLimiter.reset(key, ip);
            return sessionResponse(s, ip, null);
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            boolean recovery = req.recoveryCode() != null && !req.recoveryCode().isBlank();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error",
                    recovery ? "Código de recuperação inválido ou já usado." : WRONG_CODE));
        }
    }

    /** Carencia vencida: chave e QR para configurar, ainda sem sessao. */
    @PostMapping("/auth/setup/start")
    public ResponseEntity<Object> setupStart(@Valid @RequestBody SetupStartRequest req) {
        try {
            return ResponseEntity.ok(authService.setupStart(authService.userForSetup(req.challenge()), req.regenerate()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Sessão de login expirada. Entre de novo."));
        }
    }

    @PostMapping("/auth/setup/confirm")
    public ResponseEntity<Object> setupConfirm(@Valid @RequestBody SetupConfirmRequest req, HttpServletRequest http) {
        String key = "platform-setup:" + Integer.toHexString(req.challenge().hashCode());
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) return tooMany();
        try {
            PlatformUser u = authService.userForSetup(req.challenge());
            List<String> codes = authService.setupConfirm(u, req.code());
            rateLimiter.reset(key, ip);
            audit.record(u.getId(), "MFA_ENABLED", null, null, ip);
            return sessionResponse(authService.issueSession(u, "TOTP", client(http), false), ip, codes);
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", WRONG_CODE));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        if (p != null) authService.logout(p.sessionId());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", p.name());
        out.put("email", p.email());
        out.put("security", authService.status(authService.userOf(p.userId())));
        return out;
    }

    private ResponseEntity<Object> sessionResponse(PlatformAuthService.Session s, String ip, List<String> recoveryCodes) {
        audit.record(s.userId(), "LOGIN", null, s.method(), ip);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("step", PlatformAuthService.Step.DONE);
        body.put("name", s.name());
        body.put("email", s.email());
        if (recoveryCodes != null) body.put("recoveryCodes", recoveryCodes);
        var res = ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(s.token()).toString());
        if (s.deviceToken() != null) res.header(HttpHeaders.SET_COOKIE, cookies.device(s.deviceToken()).toString());
        return res.body(body);
    }

    private static PlatformAuthService.Client client(HttpServletRequest http) {
        return new PlatformAuthService.Client(ClientIp.of(http), http.getHeader("User-Agent"),
                PlatformSecurity.Cookies.read(http, PlatformSecurity.DEVICE_COOKIE));
    }

    private ResponseEntity<Object> tooMany() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Muitas tentativas. Tente de novo em " + rateLimiter.lockMinutes() + " minutos."));
    }
}

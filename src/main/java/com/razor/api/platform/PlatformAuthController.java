package com.razor.api.platform;

import com.razor.api.security.ClientIp;
import com.razor.api.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Login do console da plataforma (V51): {@code POST /login} (e-mail + senha) e
 * {@code POST /verify} (codigo do app autenticador). A sessao sai so no cookie HttpOnly.
 * Forca bruta: o mesmo {@link LoginRateLimiter} do painel, com chave propria.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService authService;
    private final PlatformSecurity.Cookies cookies;
    private final LoginRateLimiter rateLimiter;
    private final PlatformAuditLog audit;

    public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 1000) String challenge, @NotBlank @Size(max = 12) String code) {
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String key = "platform:" + req.email().trim().toLowerCase();
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) return tooMany();
        try {
            var step = authService.login(req.email(), req.password());
            return ResponseEntity.ok(step);
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais inválidas."));
        }
    }

    @PostMapping("/auth/verify")
    public ResponseEntity<Object> verify(@Valid @RequestBody VerifyRequest req, HttpServletRequest http) {
        String key = "platform-mfa:" + req.challenge().hashCode();
        String ip = ClientIp.of(http);
        if (rateLimiter.isBlocked(key, ip)) return tooMany();
        try {
            var s = authService.verify(req.challenge(), req.code());
            rateLimiter.reset(key, ip);
            audit.record(s.userId(), "LOGIN", null, null, ip);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookies.issue(s.token()).toString())
                    .body(Map.of("name", s.name(), "email", s.email()));
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Código inválido ou expirado. Confira o relógio do celular e tente de novo."));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        return Map.of("name", p.name(), "email", p.email());
    }

    private ResponseEntity<Object> tooMany() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Muitas tentativas. Tente de novo em " + rateLimiter.lockMinutes() + " minutos."));
    }
}

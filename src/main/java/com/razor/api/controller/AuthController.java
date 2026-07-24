package com.razor.api.controller;

import com.razor.api.security.LoginRateLimiter;
import com.razor.api.service.AuthService;
import com.razor.api.service.AuthService.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * Login do painel web: troca email + senha por um JWT (Bearer) usado nas rotas /api/admin.
 * Protegido contra forca bruta pelo {@link LoginRateLimiter} (bloqueio temporario por email+IP).
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String password) {
    }

    public record LoginResponse(String token, UUID barbershopId, String role, String name, String email) {
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = clientIp(http);

        if (rateLimiter.isBlocked(request.email(), ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Muitas tentativas. Tente de novo em " + rateLimiter.lockMinutes() + " minutos."));
        }

        try {
            LoginResult r = authService.login(request.email(), request.password());
            rateLimiter.reset(request.email(), ip);
            return ResponseEntity.ok(
                    new LoginResponse(r.token(), r.barbershopId(), r.role().name(), r.name(), r.email()));
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(request.email(), ip);
            // Mensagem generica: nao revela se o email existe.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais invalidas."));
        }
    }

    /** IP do cliente considerando proxy/load balancer na frente da aplicacao. */
    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}

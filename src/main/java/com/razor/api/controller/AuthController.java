package com.razor.api.controller;

import com.razor.api.security.LoginRateLimiter;
import com.razor.api.service.AuthService;
import com.razor.api.service.AuthService.LoginResult;
import com.razor.api.security.ClientIp;
import com.razor.api.security.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Sessao do painel web. {@code POST /login} troca email + senha por um JWT entregue <b>somente</b>
 * no cookie HttpOnly {@link SessionCookies#NAME} — o corpo traz apenas dados nao secretos do
 * usuario, para que nenhum JavaScript (nem um XSS) chegue a ver o token. Clientes de API leem o
 * token do {@code Set-Cookie} e podem manda-lo como {@code Authorization: Bearer}.
 * {@code POST /logout} apaga o cookie; {@code GET /csrf} so existe para emitir o {@code XSRF-TOKEN}.
 * A restauracao da sessao ao recarregar a pagina usa o {@code GET /api/admin/me} existente.
 *
 * <p>Login protegido contra forca bruta pelo {@link LoginRateLimiter} (bloqueio temporario por
 * email+IP).
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final SessionCookies sessionCookies;
    private final CsrfTokenRepository csrfTokenRepository;

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String password) {
    }

    /** Sem o token de proposito: ele so existe no cookie HttpOnly. */
    public record LoginResponse(UUID barbershopId, String role, String name, String email) {
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                        HttpServletResponse httpResponse) {
        String ip = ClientIp.of(http);

        if (rateLimiter.isBlocked(request.email(), ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Muitas tentativas. Tente de novo em " + rateLimiter.lockMinutes() + " minutos."));
        }

        try {
            LoginResult r = authService.login(request.email(), request.password());
            rateLimiter.reset(request.email(), ip);
            // Token CSRF novo a cada login: um XSRF-TOKEN plantado antes da autenticacao nao
            // sobrevive a ela.
            csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(http), http, httpResponse);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, sessionCookies.issue(r.token()).toString())
                    .body(new LoginResponse(r.barbershopId(), r.role().name(), r.name(), r.email()));
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(request.email(), ip);
            // Mensagem generica: nao revela se o email existe.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais invalidas."));
        }
    }

    /**
     * Encerra a sessao: apaga o cookie do JWT (e o XSRF-TOKEN, que volta novo no proximo GET).
     * Exige CSRF — sem isso qualquer site deslogaria o usuario. Nao exige estar autenticado:
     * apagar um cookie ja expirado tem que funcionar.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse httpResponse) {
        csrfTokenRepository.saveToken(null, http, httpResponse);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }

    /**
     * Nao faz nada alem de existir: o {@code CsrfCookieFilter} emite o {@code XSRF-TOKEN} em
     * qualquer rota do painel, e a SPA chama esta quando precisa do token e ainda nao tem o cookie.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}

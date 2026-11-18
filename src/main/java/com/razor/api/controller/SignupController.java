package com.razor.api.controller;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.SignupRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.TenantUser;
import com.razor.api.security.JwtService;
import com.razor.api.security.SessionCookies;
import com.razor.api.service.BarbershopProvisioningService.Provisioned;
import com.razor.api.service.SignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro sozinho pela landing (V49): {@code POST /api/signup} cria a barbearia em TRIAL e o
 * usuario dono e ja devolve a sessao aberta — mesmo cookie HttpOnly e mesmo token CSRF novo do
 * {@code POST /api/auth/login}, para o dono cair direto no assistente guiado sem digitar a senha
 * de novo.
 *
 * <p>Publico por definicao. Freios: limite por IP por hora no {@code RateLimitFilter}, honeypot e
 * e-mail unico no {@link SignupService}. Desligavel por config ({@code razor.signup.enabled}):
 * desligado responde 404, como se a rota nao existisse.
 */
@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final JwtService jwtService;
    private final SessionCookies sessionCookies;
    private final CsrfTokenRepository csrfTokenRepository;
    private final RazorProperties properties;

    /** Mesmo formato do login (o painel monta a sessao igual) + o que o assistente precisa. */
    public record SignupResponse(UUID barbershopId, String role, String name, String email,
                                 String slug, String status, LocalDateTime trialEndsAt) {
    }

    @PostMapping
    public ResponseEntity<Object> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http,
                                         HttpServletResponse httpResponse) {
        if (!properties.getSignup().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Provisioned p = signupService.signup(request);
            TenantUser owner = p.owner();
            Barbershop shop = p.shop();
            // Token CSRF novo junto com a sessao, como no login: um XSRF-TOKEN plantado antes da
            // autenticacao nao sobrevive a ela.
            csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(http), http, httpResponse);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.SET_COOKIE, sessionCookies.issue(jwtService.issue(owner)).toString())
                    .body(new SignupResponse(shop.getId(), owner.getRole().name(), owner.getName(),
                            owner.getEmail(), shop.getSlug(), shop.getStatus(), shop.getTrialEndsAt()));
        } catch (SignupService.SuspectedBotException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Campo faltando ou invalido: a primeira mensagem, em portugues, no formato {@code {error}} do
     * resto da API — o padrao do Spring devolveria um JSON tecnico que a tela nao sabe mostrar.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Confira os campos do cadastro.");
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}

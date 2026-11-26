package com.razor.api.service;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.JwtService;
import com.razor.api.security.SessionValidator;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticacao do painel web: valida email + senha (bcrypt) e emite o JWT da sessao.
 * Mensagem de erro generica (nao revela se o email existe).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionValidator sessionValidator;
    /** Barbearia suspensa pelo console não entra (26/11). Opcional: nulo nos testes. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.razor.api.repository.BarbershopRepository barbershopRepository;

    /**
     * Hash bcrypt de uma senha aleatoria, comparado quando o email nao existe (ou o usuario esta
     * inativo). Sem ele o login respondia em ~1 ms para email desconhecido e em ~80 ms (o custo
     * do bcrypt) para email cadastrado — o cronometro revelava quem tem conta, mesmo com a
     * mensagem generica. Gerado uma vez, sob demanda, com o mesmo encoder (mesmo custo).
     */
    private volatile String dummyHash;

    public record LoginResult(String token, UUID barbershopId, UserRole role, String name, String email) {
    }

    public LoginResult login(String email, String rawPassword) {
        TenantUser user = tenantUserRepository.findByEmail(normalizeEmail(email))
                .filter(TenantUser::isActive)
                .orElse(null);

        // O bcrypt roda SEMPRE, com ou sem usuario: o tempo de resposta nao pode depender de o
        // email existir.
        String hash = user != null ? user.getPasswordHash() : dummyHash();
        boolean passwordOk = passwordEncoder.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user == null || rawPassword == null || !passwordOk) {
            throw new BadCredentialsException("Credenciais invalidas.");
        }

        if (barbershopRepository != null && barbershopRepository.findById(user.getBarbershopId())
                .map(b -> "SUSPENDED".equals(b.getStatus())).orElse(false)) {
            throw new AccountSuspendedException();
        }
        log.info("[AUTH] Login do usuario {} (tenant {})", user.getEmail(), user.getBarbershopId());
        return new LoginResult(jwtService.issue(user), user.getBarbershopId(), user.getRole(),
                user.getName(), user.getEmail());
    }

    /** Conta da barbearia suspensa pelo console da plataforma. */
    public static class AccountSuspendedException extends RuntimeException {
        public AccountSuspendedException() {
            super("Conta suspensa. Fale com o suporte do Razor.");
        }
    }

    /** Usuario logado (para o /me do painel). */
    public TenantUser getUser(UUID userId) {
        return tenantUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
    }

    /**
     * Atualiza o perfil do usuario logado: nome e, opcionalmente, a senha (exige a senha atual).
     * Regras: nome em branco mantem o atual; nova senha exige minimo de 8 caracteres.
     */
    public TenantUser updateProfile(UUID userId, String name, String currentPassword, String newPassword) {
        TenantUser user = getUser(userId);

        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }

        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Senha atual incorreta.");
            }
            if (newPassword.length() < 8) {
                throw new IllegalArgumentException("A nova senha precisa de pelo menos 8 caracteres.");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            // Senha nova derruba todas as sessoes abertas com a antiga (inclusive a de quem
            // descobriu a senha vazada). Quem trocou recebe um token novo no proprio PUT /me.
            user.setTokenVersion(user.getTokenVersion() + 1);
            log.info("[AUTH] Senha alterada pelo usuario {} (tenant {})", user.getEmail(), user.getBarbershopId());
        }

        TenantUser saved = tenantUserRepository.save(user);
        sessionValidator.invalidate(saved.getId());
        return saved;
    }

    /** Emite um token novo para o usuario (ex.: apos trocar a senha, que revoga os anteriores). */
    public String reissueToken(TenantUser user) {
        return jwtService.issue(user);
    }

    /** Gera o hash falso no boot, para a primeira tentativa com email inexistente nao pagar dois bcrypts. */
    @PostConstruct
    void warmUpDummyHash() {
        dummyHash();
    }

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyHash = h;
        }
        return h;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}

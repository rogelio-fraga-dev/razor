package com.razor.api.security;

import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revalida o JWT contra o estado atual do usuario. A assinatura so prova que o token foi emitido
 * por nos — nao que o usuario ainda deve entrar. Sem esta checagem, desativar um funcionario ou
 * trocar uma senha vazada nao tirava ninguem do painel ate o token vencer (12h).
 *
 * <p>O token e aceito se o usuario existe, esta ativo, tem o mesmo papel do token e a mesma
 * {@code token_version}. Para nao custar uma consulta por requisicao, o estado fica em cache por
 * {@link #TTL}: uma desativacao feita direto no banco leva no maximo isso para valer; as mudancas
 * feitas pela aplicacao (troca de senha) chamam {@link #invalidate} e valem na hora.
 */
@Component
public class SessionValidator {

    static final Duration TTL = Duration.ofSeconds(30);

    private record UserState(boolean active, int version, UserRole role, Instant loadedAt) {
    }

    private final TenantUserRepository repository;
    private final Clock clock;
    private final Map<UUID, UserState> cache = new ConcurrentHashMap<>();

    @Autowired
    public SessionValidator(TenantUserRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SessionValidator(TenantUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean isValid(TenantUserPrincipal principal, int tokenVersion) {
        UserState s = state(principal.userId());
        return s.active() && s.version() == tokenVersion && s.role() == principal.role();
    }

    /** Descarta o estado em cache — chamar apos mudar senha, papel, ativo ou versao. */
    public void invalidate(UUID userId) {
        cache.remove(userId);
    }

    private UserState state(UUID userId) {
        Instant now = clock.instant();
        UserState cached = cache.get(userId);
        if (cached != null && now.isBefore(cached.loadedAt().plus(TTL))) {
            return cached;
        }
        UserState fresh = repository.findById(userId)
                .map(u -> new UserState(u.isActive(), u.getTokenVersion(), u.getRole(), now))
                // Inexistente tambem vai para o cache (como inativo): token de usuario apagado
                // nao bate no banco a cada requisicao.
                .orElse(new UserState(false, -1, null, now));
        cache.put(userId, fresh);
        return fresh;
    }

    /** Varre entradas vencidas para o cache nao crescer com usuarios que nao voltam. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant cutoff = clock.instant().minus(TTL);
        cache.entrySet().removeIf(e -> e.getValue().loadedAt().isBefore(cutoff));
    }
}

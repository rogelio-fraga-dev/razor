package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Revalidacao do JWT contra o estado do usuario (ativo, papel, versao), com cache curto. */
@DisplayName("Revalidação da sessão (usuário ativo + versão do token)")
class SessionValidatorTest {

    private TenantUserRepository repository;
    private MutableClock clock;
    private SessionValidator validator;
    private TenantUser user;
    private TenantUserPrincipal principal;

    @BeforeEach
    void setUp() {
        repository = mock(TenantUserRepository.class);
        clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        validator = new SessionValidator(repository, clock);
        user = TenantUser.builder().id(UUID.randomUUID()).email("a@loja.com").barbershopId(UUID.randomUUID())
                .passwordHash("h").role(UserRole.RECEPTION).active(true).tokenVersion(3).build();
        principal = new TenantUserPrincipal(user.getId(), user.getEmail(), user.getBarbershopId(), UserRole.RECEPTION);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("usuário ativo, mesmo papel e mesma versão: válido")
    void activeSameVersion_valid() {
        assertThat(validator.isValid(principal, 3)).isTrue();
    }

    @Test
    @DisplayName("versão antiga (senha trocada depois da emissão): revogado")
    void oldVersion_invalid() {
        assertThat(validator.isValid(principal, 2)).isFalse();
    }

    @Test
    @DisplayName("papel do token diferente do atual: revogado")
    void roleChanged_invalid() {
        var asOwner = new TenantUserPrincipal(user.getId(), user.getEmail(), user.getBarbershopId(), UserRole.OWNER);
        assertThat(validator.isValid(asOwner, 3)).isFalse();
    }

    @Test
    @DisplayName("usuário inexistente: revogado")
    void missingUser_invalid() {
        var ghost = new TenantUserPrincipal(UUID.randomUUID(), "x@y.com", UUID.randomUUID(), UserRole.OWNER);
        assertThat(validator.isValid(ghost, 0)).isFalse();
    }

    @Test
    @DisplayName("cache: uma consulta por TTL; desativação no banco vale após o TTL")
    void cache_respectsTtl() {
        assertThat(validator.isValid(principal, 3)).isTrue();
        user.setActive(false); // desativado direto no banco
        assertThat(validator.isValid(principal, 3)).isTrue(); // ainda no cache
        verify(repository, times(1)).findById(user.getId());

        clock.advance(SessionValidator.TTL.plusSeconds(1));

        assertThat(validator.isValid(principal, 3)).isFalse();
        verify(repository, times(2)).findById(user.getId());
    }

    @Test
    @DisplayName("invalidate faz a mudança valer na hora")
    void invalidate_appliesImmediately() {
        assertThat(validator.isValid(principal, 3)).isTrue();
        user.setTokenVersion(4);
        validator.invalidate(user.getId());

        assertThat(validator.isValid(principal, 3)).isFalse();
        assertThat(validator.isValid(principal, 4)).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

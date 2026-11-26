package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes do {@link JwtService}: round-trip emitir/validar e rejeicao de token adulterado. */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        RazorProperties props = new RazorProperties();
        props.getSecurity().setJwtSecret("um-segredo-de-teste-bem-longo-0123456789");
        props.getSecurity().setJwtExpirationMinutes(60);
        jwtService = new JwtService(props);
    }

    private TenantUser user() {
        return TenantUser.builder()
                .id(UUID.randomUUID())
                .email("dono@loja.com")
                .barbershopId(UUID.randomUUID())
                .role(UserRole.OWNER)
                .build();
    }

    @Test
    @DisplayName("token emitido é validado e devolve o principal com tenant e papel")
    void issueThenParse_roundTrip() {
        TenantUser u = user();

        TenantUserPrincipal principal = jwtService.parse(jwtService.issue(u));

        assertThat(principal.userId()).isEqualTo(u.getId());
        assertThat(principal.email()).isEqualTo("dono@loja.com");
        assertThat(principal.barbershopId()).isEqualTo(u.getBarbershopId());
        assertThat(principal.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    @DisplayName("token adulterado/garbage é rejeitado")
    void parse_invalidToken_throws() {
        assertThatThrownBy(() -> jwtService.parse("nao-e-um-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token assinado com outra chave é rejeitado")
    void parse_tokenFromAnotherKey_throws() {
        RazorProperties other = new RazorProperties();
        other.getSecurity().setJwtSecret("outro-segredo-completamente-diferente-99");
        String foreignToken = new JwtService(other).issue(user());

        assertThatThrownBy(() -> jwtService.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a versão do usuário viaja no token (claim ver) para a revalidação")
    void issueThenParse_carriesTokenVersion() {
        TenantUser u = user();
        u.setTokenVersion(7);

        JwtService.ParsedToken parsed = jwtService.parseToken(jwtService.issue(u));

        assertThat(parsed.version()).isEqualTo(7);
        assertThat(parsed.principal().userId()).isEqualTo(u.getId());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("sessão de suporte do console sai como só leitura; a normal não")
    void readOnlyToken() {
        org.assertj.core.api.Assertions.assertThat(jwtService.parseToken(jwtService.issueReadOnly(user(), 30)).readOnly()).isTrue();
        org.assertj.core.api.Assertions.assertThat(jwtService.parseToken(jwtService.issue(user())).readOnly()).isFalse();
    }
}

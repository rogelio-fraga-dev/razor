package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.SessionCookies;
import com.razor.api.security.SessionValidator;
import com.razor.api.tenant.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida a cadeia de seguranca contra o contexto real: o webhook e publico (Security nao pode
 * quebra-lo), {@code /api/admin/**} exige JWT (401 sem token), e o fluxo login -> Bearer -> /me
 * funciona (agora via cookie HttpOnly + CSRF). Pulado sem Docker (dev local); roda no CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Cadeia de segurança (webhook público, /api/admin protegido, login JWT)")
class SecurityConfigIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("razor.openai.api-key", () -> "test-key");
        registry.add("razor.admin.phones", () -> "5511900000000");
        registry.add("razor.security.jwt-secret", () -> "segredo-de-teste-do-IT-0123456789ABCDEF");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantUserRepository tenantUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SessionValidator sessionValidator;

    @BeforeEach
    void seedUsers() {
        seedUser("it@loja.com", "IT Owner", UserRole.OWNER);
        seedUser("recepcao@loja.com", "IT Recepção", UserRole.RECEPTION);
        seedUser("barbeiro@loja.com", "IT Barbeiro", UserRole.BARBER);
    }

    private void seedUser(String email, String name, UserRole role) {
        if (tenantUserRepository.findByEmail(email).isEmpty()) {
            tenantUserRepository.save(TenantUser.builder()
                    .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                    .email(email)
                    .passwordHash(passwordEncoder.encode("senha1234"))
                    .name(name)
                    .role(role)
                    .active(true)
                    .build());
        }
    }

    @Test
    @DisplayName("webhook health continua público (Security não quebra o webhook)")
    void webhookHealth_isPublic() throws Exception {
        mockMvc.perform(get("/api/webhook/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/admin/me sem token responde 401")
    void adminMe_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login emite token e /api/admin/me com Bearer responde 200")
    void loginThenAccessAdmin() throws Exception {
        String body = "{\"email\":\"it@loja.com\",\"password\":\"senha1234\"}";
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        String token = sessionToken(login);
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // endpoint real de config (catalogo do tenant) tambem responde com o token
        mockMvc.perform(get("/api/admin/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").isArray());
    }

    @Test
    @DisplayName("config sem token responde 401 (protegida)")
    void adminServices_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/services")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("relatórios serializam java.time (LocalDate/LocalTime) — 200, não 500")
    void reports_serializeJavaTime() throws Exception {
        String token = login();

        // /financial devolve LocalDate (from/to): sem o JavaTimeModule isso quebrava com 500.
        mockMvc.perform(get("/api/admin/reports/financial").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.from").isString());

        // /agenda devolve LocalTime nos itens: idem — precisa serializar sem estourar.
        mockMvc.perform(get("/api/admin/reports/agenda").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").isArray());
    }

    private String login() throws Exception {
        return login("it@loja.com");
    }

    private String login(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"senha1234\"}";
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return sessionToken(login);
    }

    /** O JWT so sai no cookie HttpOnly — clientes de API (e estes testes) o leem do Set-Cookie. */
    private static String sessionToken(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie(SessionCookies.NAME);
        assertThat(cookie).as("cookie de sessao no login").isNotNull();
        return cookie.getValue();
    }

    // ---------------------------------------------------------------------------------------
    // Sessao por cookie HttpOnly + CSRF (o painel nao guarda mais o JWT no localStorage).
    // ---------------------------------------------------------------------------------------

    private static final String CSRF = "csrf-de-teste-123";
    private static final String NAME_BODY = "{\"name\":\"IT Owner\"}";

    @Test
    @DisplayName("login entrega o JWT só no cookie HttpOnly/SameSite=Strict/Path=/api — nunca no corpo")
    void login_setsHttpOnlyCookie_andNoTokenInBody() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it@loja.com\",\"password\":\"senha1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn();

        String setCookie = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(SessionCookies.NAME + "="))
                .findFirst().orElseThrow();
        // Testes rodam com o padrao: Secure ligado. Max-Age = TTL do token (720 min).
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api", "Max-Age=43200");

        // Token CSRF novo emitido junto com o login, legivel pelo JS (sem HttpOnly).
        // (lido pelo objeto Cookie: o mock do servlet nao serializa atributos como SameSite no
        // header, o Tomcat sim.)
        Cookie xsrf = login.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        assertThat(xsrf.getPath()).isEqualTo("/");
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    @DisplayName("cookie de sessão autentica leitura sem header Authorization")
    void sessionCookie_authenticatesGet() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/admin/me").cookie(new Cookie(SessionCookies.NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it@loja.com"));
    }

    @Test
    @DisplayName("GET no painel emite o XSRF-TOKEN (o token deferred do Spring 6 é carregado)")
    void panelGet_emitsCsrfCookie() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = r.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();
        assertThat(xsrf.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("escrita autenticada por cookie SEM token CSRF responde 403 {code: CSRF}")
    void cookieWrite_withoutCsrf_forbidden() throws Exception {
        String token = login();
        mockMvc.perform(put("/api/admin/me")
                        .cookie(new Cookie(SessionCookies.NAME, token))
                        .contentType(MediaType.APPLICATION_JSON).content(NAME_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));

        // Cookie XSRF presente mas header divergente: idem (o atacante nao consegue ler o cookie).
        mockMvc.perform(put("/api/admin/me")
                        .cookie(new Cookie(SessionCookies.NAME, token), new Cookie("XSRF-TOKEN", CSRF))
                        .header("X-XSRF-TOKEN", "outro-valor")
                        .contentType(MediaType.APPLICATION_JSON).content(NAME_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
    }

    @Test
    @DisplayName("escrita autenticada por cookie COM double-submit CSRF passa")
    void cookieWrite_withCsrf_ok() throws Exception {
        String token = login();
        mockMvc.perform(put("/api/admin/me")
                        .cookie(new Cookie(SessionCookies.NAME, token), new Cookie("XSRF-TOKEN", CSRF))
                        .header("X-XSRF-TOKEN", CSRF)
                        .contentType(MediaType.APPLICATION_JSON).content(NAME_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("header Authorization manda: Bearer inválido não cai no cookie")
    void invalidBearer_doesNotFallBackToCookie() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/admin/me")
                        .cookie(new Cookie(SessionCookies.NAME, token))
                        .header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout exige CSRF e apaga o cookie de sessão")
    void logout_requiresCsrf_andClearsCookie() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(SessionCookies.NAME, token)))
                .andExpect(status().isForbidden());

        MvcResult r = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(SessionCookies.NAME, token), new Cookie("XSRF-TOKEN", CSRF))
                        .header("X-XSRF-TOKEN", CSRF))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cleared = r.getResponse().getCookie(SessionCookies.NAME);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Revalidacao: assinatura valida nao basta se o usuario mudou depois da emissao.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("trocar a senha derruba os tokens antigos e reemite o cookie de quem trocou")
    void passwordChange_revokesOldTokens_andReissuesCookie() throws Exception {
        seedUser("troca@loja.com", "IT Troca", UserRole.BARBER);
        String antigo = login("troca@loja.com");
        String outraSessao = login("troca@loja.com");

        MvcResult r = mockMvc.perform(put("/api/admin/me")
                        .cookie(new Cookie(SessionCookies.NAME, antigo), new Cookie("XSRF-TOKEN", CSRF))
                        .header("X-XSRF-TOKEN", CSRF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha1234\",\"newPassword\":\"novaSenha123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String novo = sessionToken(r);

        // Sessoes abertas com a senha antiga (inclusive outro aparelho) caem na hora.
        mockMvc.perform(get("/api/admin/me").cookie(new Cookie(SessionCookies.NAME, outraSessao)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + antigo))
                .andExpect(status().isUnauthorized());
        // Quem trocou segue logado com o cookie novo.
        mockMvc.perform(get("/api/admin/me").cookie(new Cookie(SessionCookies.NAME, novo)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("usuário desativado perde o acesso sem esperar o token vencer")
    void deactivatedUser_tokenRejected() throws Exception {
        seedUser("demitido@loja.com", "IT Demitido", UserRole.RECEPTION);
        String token = login("demitido@loja.com");
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        TenantUser u = tenantUserRepository.findByEmail("demitido@loja.com").orElseThrow();
        u.setActive(false);
        tenantUserRepository.save(u);
        // Desativacao feita fora do AuthService (aqui, direto no repositorio) vale apos o TTL do
        // cache; o teste nao espera 30 s e descarta o cache como faria o fluxo da aplicacao.
        sessionValidator.invalidate(u.getId());

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login: email inexistente e senha errada respondem igual (401 genérico)")
    void login_unknownEmail_sameAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@loja.com\",\"password\":\"qualquer1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciais invalidas."));
    }

    @Test
    @DisplayName("rotas fora do painel seguem sem CSRF: webhook e onboarding")
    void publicWrites_doNotRequireCsrf() throws Exception {
        // Webhook e onboarding respondem pelo proprio segredo (aqui ausente, entao recusam) — o
        // que importa e que a recusa nao venha do CSRF.
        // Webhook sem token: 401 do proprio controller (o CSRF daria 403).
        mockMvc.perform(post("/api/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // Onboarding com corpo vazio: chega ao controller e cai na validacao (400), nao no CSRF.
        mockMvc.perform(post("/api/onboarding").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------
    // RBAC: token valido nao basta — o papel decide. Enquanto so o dono usava o painel isso era
    // teoria; deixa de ser no instante em que um funcionario ganha login.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("RECEPÇÃO opera o balcão (comandas, caixa) mas não vê comissão nem financeiro")
    void reception_operatesCounter_butNotMoneyReports() throws Exception {
        String token = login("recepcao@loja.com");

        mockMvc.perform(get("/api/admin/comandas/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/cash-register").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Comissao de colega e faturamento da casa nao sao assunto da recepcao.
        mockMvc.perform(get("/api/admin/reports/commissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/financial").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commission-payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RECEPÇÃO lê o catálogo para montar a comanda, mas não altera preço")
    void reception_readsCatalog_butCannotWritePrices() throws Exception {
        String token = login("recepcao@loja.com");

        mockMvc.perform(get("/api/admin/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // A separacao leitura/escrita e o ponto: sem ela, dar acesso a tela de comanda daria
        // junto o poder de reajustar a tabela de precos.
        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte\",\"price\":35.00,\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BARBEIRO vê a agenda do dia e o próprio perfil, e nada além disso")
    void barber_seesAgendaAndOwnProfile_only() throws Exception {
        String token = login("barbeiro@loja.com");

        mockMvc.perform(get("/api/admin/reports/agenda").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/comandas/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/commissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DONO alcança configuração e dinheiro")
    void owner_reachesConfigAndMoney() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/admin/reports/commissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/professionals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/settings/bot").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("403 sai com corpo {error} — o painel precisa distinguir 'sem permissão' de bug")
    void forbidden_hasJsonBody() throws Exception {
        String token = login("barbeiro@loja.com");

        mockMvc.perform(get("/api/admin/reports/financial").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("senha errada no login responde 401")
    void login_wrongPassword_unauthorized() throws Exception {
        String body = "{\"email\":\"it@loja.com\",\"password\":\"errada\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

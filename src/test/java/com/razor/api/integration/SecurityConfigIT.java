package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
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
 * funciona. Pulado sem Docker (dev local); roda no CI.
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
    @Autowired private ObjectMapper objectMapper;

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

        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
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
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
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
    @DisplayName("RECEPÇÃO não altera o plano do cliente (só o dono concede assinatura)")
    void reception_cannotChangeCustomerPlan() throws Exception {
        String token = login("recepcao@loja.com");
        String anyId = java.util.UUID.randomUUID().toString();

        // A recepcao continua vendo clientes...
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // ...mas o PUT do plano e barrado antes de chegar no controller.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/customers/" + anyId + "/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("senha errada no login responde 401")
    void login_wrongPassword_unauthorized() throws Exception {
        String body = "{\"email\":\"it@loja.com\",\"password\":\"errada\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

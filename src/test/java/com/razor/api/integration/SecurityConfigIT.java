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
    void seedOwner() {
        if (tenantUserRepository.findByEmail("it@loja.com").isEmpty()) {
            tenantUserRepository.save(TenantUser.builder()
                    .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                    .email("it@loja.com")
                    .passwordHash(passwordEncoder.encode("senha1234"))
                    .name("IT Owner")
                    .role(UserRole.OWNER)
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
    }

    @Test
    @DisplayName("senha errada no login responde 401")
    void login_wrongPassword_unauthorized() throws Exception {
        String body = "{\"email\":\"it@loja.com\",\"password\":\"errada\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

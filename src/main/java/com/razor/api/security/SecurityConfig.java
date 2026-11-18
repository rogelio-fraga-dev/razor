package com.razor.api.security;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguranca do painel web: API stateless autenticada por JWT (Bearer). Endpoints publicos
 * (webhook da Evolution, login, onboarding, health) ficam liberados; {@code /api/admin/**} exige
 * token valido <b>e o papel certo</b>. CSRF desligado (API sem sessao/cookie) e CORS restrito.
 *
 * <p><b>Por que a autorizacao mora aqui e nao em {@code @PreAuthorize} espalhado:</b> a regra de
 * quem enxerga dinheiro precisa caber numa tela so. Espalhada pelos ~25 controllers, a pergunta
 * "o barbeiro consegue ver a comissao dos colegas?" vira uma busca no repositorio inteiro, e
 * controller novo nasce sem regra nenhuma.
 *
 * <p><b>A ordem importa e o ultimo casamento e a rede.</b> O Spring aplica a primeira regra que
 * casa, entao as excecoes vem antes do geral. O fecho de {@code /api/admin/**} e
 * {@code hasRole(OWNER)}: um controller novo que ninguem classificou nasce restrito ao dono, e
 * o sintoma e "a recepcao nao consegue usar a tela nova" — visivel e reversivel. O inverso
 * (nascer liberado) so apareceria como vazamento, e provavelmente tarde demais.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RazorProperties properties;

    // Nomes dos papeis SEM o prefixo ROLE_: e o que hasRole/hasAnyRole esperam (o Spring
    // adiciona o prefixo). Constantes para que um erro de digitacao vire erro de compilacao
    // em vez de uma regra que silenciosamente nunca casa — e libera geral pela regra seguinte.
    private static final String OWNER = UserRole.OWNER.name();
    private static final String RECEPTION = UserRole.RECEPTION.name();
    private static final String BARBER = UserRole.BARBER.name();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // O site white-label e publico por definicao — um site que so abre com
                        // login nao e um site. O isolamento vem do slug da URL, e o que sai e
                        // limitado pelo PublicSiteResponse: nenhum dado de cliente, faturamento
                        // ou comissao passa por ali.
                        .requestMatchers(HttpMethod.GET, "/api/site/**").permitAll()
                        .requestMatchers("/api/webhook/**", "/api/auth/**", "/api/onboarding",
                                "/actuator/health", "/error").permitAll()

                        // --- Proprio usuario -------------------------------------------------
                        // Ler e editar o proprio perfil nao depende de papel.
                        .requestMatchers("/api/admin/me").authenticated()

                        // --- Agenda do dia ---------------------------------------------------
                        // O barbeiro precisa ver com quem atende hoje. Vem ANTES da regra geral
                        // de /reports/** porque os outros relatorios sao financeiros.
                        .requestMatchers(HttpMethod.GET, "/api/admin/reports/agenda")
                        .hasAnyRole(OWNER, RECEPTION, BARBER)

                        // --- Agenda interativa (V39) -----------------------------------------
                        // Ler a grade: todo mundo (o barbeiro ve com quem atende). Mexer nela
                        // (criar, arrastar, cancelar, comparecimento): dono e recepcao. O barbeiro
                        // fica so na leitura porque o usuario do painel ainda nao e ligado a um
                        // cadastro de profissional — nao ha como restringi-lo "aos proprios".
                        .requestMatchers(HttpMethod.GET, "/api/admin/agenda", "/api/admin/agenda/**")
                        .hasAnyRole(OWNER, RECEPTION, BARBER)
                        .requestMatchers("/api/admin/agenda", "/api/admin/agenda/**")
                        .hasAnyRole(OWNER, RECEPTION)

                        // --- Operacao do balcao ----------------------------------------------
                        // Recepcao abre comanda, vende, mexe no caixa e bloqueia horario. Nao
                        // configura preco, comissao nem o bot.
                        .requestMatchers(
                                "/api/admin/comandas/**",
                                "/api/admin/cash-register/**",
                                "/api/admin/product-sales/**",
                                "/api/admin/customers/**",
                                "/api/admin/schedule-blocks/**")
                        .hasAnyRole(OWNER, RECEPTION)

                        // --- Catalogo em leitura ---------------------------------------------
                        // A tela de comanda precisa listar servicos e produtos para montar a
                        // conta; escrever no catalogo (preco) continua sendo so do dono.
                        .requestMatchers(HttpMethod.GET, "/api/admin/services/**", "/api/admin/products/**")
                        .hasAnyRole(OWNER, RECEPTION)

                        // --- Tudo mais sob /api/admin: so o dono ------------------------------
                        // Comissoes, acerto, vales, relatorios financeiros, planos, cupons,
                        // estoque, catalogo (escrita), profissionais, equipe, config do bot,
                        // pareamento do WhatsApp e perfil da barbearia.
                        .requestMatchers("/api/admin/**").hasRole(OWNER)

                        .anyRequest().authenticated())
                // API JWT: acesso sem token (ou token invalido) retorna 401 — o SPA usa isso para
                // disparar o re-login. Sem isso o padrao do Spring seria 403 para o anonimo.
                // Ja o 403 (token valido, papel insuficiente) sai com corpo {"error": ...}, no
                // mesmo formato do ApiExceptionHandler: sem isso o painel exibiria "Erro 403"
                // seco, indistinguivel de um bug.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\":\"Seu perfil não tem acesso a esta função.\"}");
                        }))
                // Cabecalhos de seguranca: a API nunca e enquadrada, nao adivinha content-type e
                // nao vaza a URL interna no Referer. HSTS so faz sentido atras de HTTPS (producao).
                .headers(h -> h
                        .frameOptions(fo -> fo.deny())
                        .contentTypeOptions(cto -> {})
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(properties.getSecurity().getCorsAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

package com.razor.api.security;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguranca do painel web: API stateless autenticada por JWT. Endpoints publicos (webhook da
 * Evolution, login, onboarding, site, health) ficam liberados; {@code /api/admin/**} exige token
 * valido <b>e o papel certo</b>. CORS restrito.
 *
 * <p><b>Cookie + CSRF.</b> O painel recebe o JWT num cookie HttpOnly ({@link SessionCookies}),
 * fora do alcance de um XSS. Como o navegador anexa cookie sozinho, toda escrita autenticada por
 * cookie exige o token CSRF (double-submit: cookie {@code XSRF-TOKEN} legivel pelo JS, devolvido
 * no header {@code X-XSRF-TOKEN}). A exigencia vale para metodos de escrita em
 * {@code /api/admin/**} e {@code /api/auth/logout}; webhook (token proprio), onboarding (chave no
 * header), site publico e o proprio login nao usam o cookie de sessao e ficam de fora. Requisicao
 * com header {@code Authorization} tambem fica de fora: o {@link JwtAuthenticationFilter} nunca a
 * autentica pelo cookie, e um site de terceiros nao consegue forjar esse header (exige preflight
 * de CORS, que so libera as origens do painel).
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

    /** Metodos que nao mudam estado — nunca exigem CSRF. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Rotas autenticadas por cookie onde uma escrita precisa provar a origem (token CSRF). */
    private static final RequestMatcher CSRF_PATHS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/admin/**"),
            new AntPathRequestMatcher("/api/auth/logout"));

    /**
     * Exige CSRF em escrita nas {@link #CSRF_PATHS}, exceto quando a requisicao traz
     * {@code Authorization} — ver javadoc da classe.
     */
    static final RequestMatcher CSRF_REQUIRED = request ->
            !SAFE_METHODS.contains(request.getMethod())
                    && request.getHeader("Authorization") == null
                    && CSRF_PATHS.matches(request);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Repositorio do token CSRF: cookie {@code XSRF-TOKEN} <b>legivel</b> pelo JS (e o que o
     * double-submit pede — o segredo de verdade, o JWT, continua HttpOnly), {@code Path=/} para
     * que qualquer rota da SPA consiga le-lo, e {@code SameSite=Strict}.
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        boolean secure = properties.getSecurity().isCookieSecure();
        repo.setCookieCustomizer(c -> c.secure(secure).sameSite("Strict"));
        return repo;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Handler "plano" (sem a mascara XOR do padrao do Spring 6): a SPA devolve no
                // header exatamente o valor do cookie. O XOR protege contra BREACH em token
                // renderizado no HTML, o que nao e o caso aqui.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(CSRF_REQUIRED)
                        // Sem sessao, o JWT autentica a CADA requisicao — e o CsrfAuthenticationStrategy
                        // padrao trata isso como "login novo" e APAGA o cookie XSRF-TOKEN (Max-Age=0)
                        // em todo GET autenticado. O painel entao precisava buscar token novo antes de
                        // cada escrita, e duas escritas seguidas brigavam pelo token. Rotacao de token
                        // fica no login (AuthController), nao em toda requisicao.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // O site white-label e publico por definicao — um site que so abre com
                        // login nao e um site. O isolamento vem do slug da URL, e o que sai e
                        // limitado pelo PublicSiteResponse: nenhum dado de cliente, faturamento
                        // ou comissao passa por ali.
                        // Inclui as fotos do site (V45), GET /api/site/img/{uuid}: publicas por
                        // UUID aleatorio, servidas com nosniff e tipo detectado pelos bytes.
                        .requestMatchers(HttpMethod.GET, "/api/site/**").permitAll()
                        // Agendamento online (V38): o unico POST publico do site. Contido pelo
                        // limite por IP proprio no RateLimitFilter e pelo honeypot.
                        .requestMatchers(HttpMethod.POST, "/api/site/*/booking").permitAll()
                        .requestMatchers("/api/webhook/**", "/api/auth/**", "/api/onboarding",
                                "/actuator/health", "/error").permitAll()

                        // --- Proprio usuario -------------------------------------------------
                        // Ler e editar o proprio perfil nao depende de papel.
                        .requestMatchers("/api/admin/me").authenticated()
                        // "Quanto eu ganhei" (V44): escopado no profissional do PROPRIO login.
                        // Barbeiro pedindo outro professionalId leva 403 no MyEarningsService.
                        .requestMatchers(HttpMethod.GET, "/api/admin/me/earnings")
                        .hasAnyRole(OWNER, RECEPTION, BARBER)

                        // --- Agenda do dia ---------------------------------------------------
                        // O barbeiro precisa ver com quem atende hoje. Vem ANTES da regra geral
                        // de /reports/** porque os outros relatorios sao financeiros.
                        .requestMatchers(HttpMethod.GET, "/api/admin/reports/agenda")
                        .hasAnyRole(OWNER, RECEPTION, BARBER)
                        // "O que a IA fez por voce" (V42): contagens e valor de tabela do que o
                        // bot marcou — a recepcao ve no Dashboard; o barbeiro nao.
                        .requestMatchers(HttpMethod.GET, "/api/admin/reports/ai-impact")
                        .hasAnyRole(OWNER, RECEPTION)
                        // Banner de saude (V50): "WhatsApp desconectado" tambem para a recepcao,
                        // que e quem percebe primeiro. O historico completo fica com o dono.
                        .requestMatchers(HttpMethod.GET, "/api/admin/system-health/alerts")
                        .hasAnyRole(OWNER, RECEPTION)

                        // --- Plano do cliente: so o dono ------------------------------------
                        // Dar ou tirar assinatura muda quanto o cliente paga (e o que a IA cobra
                        // dele). Vem ANTES da regra do balcao, que libera /customers/** para a
                        // recepcao — sem isso a recepcao concederia plano de graca.
                        .requestMatchers(HttpMethod.PUT, "/api/admin/customers/*/plan").hasRole(OWNER)
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
                        // CSRF ausente/errado tambem cai aqui (CsrfException e um AccessDenied),
                        // mas com outra mensagem: nao e falta de papel, e a pagina desatualizada.
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(denied instanceof CsrfException
                                    ? "{\"error\":\"Sessão desatualizada. Recarregue a página.\",\"code\":\"CSRF\"}"
                                    : "{\"error\":\"Seu perfil não tem acesso a esta função.\"}");
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // O token CSRF do Spring 6 e "deferred": so vira cookie se alguem o ler. Sem este
                // filtro o XSRF-TOKEN nunca chegaria a SPA e toda escrita levaria 403.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
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

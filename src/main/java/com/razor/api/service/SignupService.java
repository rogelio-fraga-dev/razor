package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.SignupRequest;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.service.BarbershopProvisioningService.Provisioned;
import com.razor.api.service.BarbershopProvisioningService.TrialSignup;
import com.razor.api.util.PhoneNumbers;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Cadastro sozinho pela landing (V49): valida o que o dono digitou, escolhe um endereco (slug)
 * livre a partir do nome da barbearia e delega a criacao ao {@link BarbershopProvisioningService}
 * — o mesmo caminho do provisionamento da plataforma, para que barbearia cadastrada pela landing
 * e barbearia criada pelo operador nascam iguais (catalogo, barbeiro, admin, dono).
 *
 * <p><b>Sem cobranca.</b> A barbearia nasce em TRIAL com {@code trial_ends_at}; o fim do teste
 * nao bloqueia nada enquanto o modelo de cobranca nao for decidido (docs/MVP-PILOTO.md).
 *
 * <p><b>Abuso</b> e contido em camadas: limite por IP no {@code RateLimitFilter} (por hora),
 * honeypot aqui, e-mail unico na plataforma e a mesma regra de senha do resto do painel.
 */
@Slf4j
@Service
public class SignupService {

    /** Mesma regra da troca de senha (AuthService.updateProfile). */
    static final int MIN_PASSWORD = 8;
    /** Slug curto o bastante para caber num cartao e virar subdominio no futuro (rotulo DNS). */
    static final int MAX_SLUG = 40;
    private static final Set<String> PLANS = Set.of("SOLO", "EQUIPE", "PRO");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    /**
     * Enderecos que nao podem virar barbearia: rotas do painel/landing hoje e subdominios que a
     * plataforma vai querer quando houver dominio. "barbearia-api" passa; "api" nao.
     */
    static final Set<String> RESERVED = Set.of(
            "admin", "api", "app", "www", "painel", "login", "cadastro", "comecar", "site", "b",
            "blog", "ajuda", "suporte", "status", "mail", "email", "razor", "static", "cdn",
            "termos", "privacidade", "cancelamento", "dev", "teste", "test");

    private final BarbershopProvisioningService provisioningService;
    private final BarbershopRepository barbershopRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RazorProperties properties;
    private final Clock clock;

    @Autowired
    public SignupService(BarbershopProvisioningService provisioningService,
                         BarbershopRepository barbershopRepository,
                         TenantUserRepository tenantUserRepository,
                         RazorProperties properties) {
        this(provisioningService, barbershopRepository, tenantUserRepository, properties, Clock.system(ZONE));
    }

    /** Relogio injetavel para os testes conferirem a data de fim do teste. */
    SignupService(BarbershopProvisioningService provisioningService, BarbershopRepository barbershopRepository,
                  TenantUserRepository tenantUserRepository, RazorProperties properties, Clock clock) {
        this.provisioningService = provisioningService;
        this.barbershopRepository = barbershopRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Cadastro recusado pelo honeypot: o controller responde de forma generica. */
    public static class SuspectedBotException extends RuntimeException {
        public SuspectedBotException() {
            super("Não foi possível concluir o cadastro.");
        }
    }

    public Provisioned signup(SignupRequest req) {
        // Honeypot: mensagem generica, sem dizer o porque — dizer ensinaria o robo a desviar.
        if (req.website() != null && !req.website().isBlank()) {
            log.warn("[CADASTRO] Honeypot preenchido — cadastro descartado.");
            throw new SuspectedBotException();
        }

        String shopName = clean(req.shopName());
        String ownerName = clean(req.ownerName());
        String city = clean(req.city());
        String email = AuthService.normalizeEmail(req.email());
        String password = req.password() == null ? "" : req.password();
        if (shopName.length() < 2) throw new IllegalArgumentException("Informe o nome da barbearia.");
        if (ownerName.length() < 2) throw new IllegalArgumentException("Informe o seu nome.");
        if (city.length() < 2) throw new IllegalArgumentException("Informe a cidade.");
        if (password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("A senha precisa de pelo menos " + MIN_PASSWORD + " caracteres.");
        }
        if (password.isBlank()) throw new IllegalArgumentException("A senha não pode ser só espaços.");
        String phone = PhoneNumbers.canonicalBrazilian(req.whatsapp())
                .orElseThrow(() -> new IllegalArgumentException("Informe um WhatsApp válido, com DDD."));
        String plan = plan(req.plan());

        // Checado antes de escolher slug: e o erro mais comum (quem ja tem conta), e a mensagem
        // precisa levar ao login em vez de um "conflito" generico.
        if (tenantUserRepository.existsByEmail(email)) {
            throw new ConflictException("Este e-mail já tem conta. Entre pelo login.");
        }

        LocalDateTime trialEnds = LocalDateTime.now(clock).plusDays(properties.getSignup().getTrialDays());
        // Duas tentativas: outro cadastro com o mesmo nome no mesmo instante pode pegar o slug
        // entre a checagem e a gravacao (o indice unico recusa); a segunda escolhe o proximo.
        for (int attempt = 0; ; attempt++) {
            String slug = availableSlug(shopName);
            try {
                Provisioned p = provisioningService.provisionTrial(new TrialSignup(
                        shopName, slug, city, plan, ownerName, email, phone, password, trialEnds));
                log.info("[CADASTRO] Barbearia '{}' cadastrada pela landing (slug {}, plano {}, cidade {})",
                        shopName, slug, plan, city);
                return p;
            } catch (OnboardingConflictException | DataIntegrityViolationException e) {
                if (tenantUserRepository.existsByEmail(email)) {
                    throw new ConflictException("Este e-mail já tem conta. Entre pelo login.");
                }
                if (attempt >= 1) throw new ConflictException("Não foi possível concluir o cadastro. Tente de novo.");
            }
        }
    }

    /** Primeiro slug livre derivado do nome: "barbearia-do-ze", "barbearia-do-ze-2", ... */
    String availableSlug(String shopName) {
        String base = slugify(shopName);
        if (base.length() < 3 || RESERVED.contains(base)) {
            base = (base.isEmpty() ? "barbearia" : "barbearia-" + base);
            base = trim(base);
        }
        if (isFree(base)) return base;
        for (int n = 2; n < 1000; n++) {
            String suffix = "-" + n;
            String candidate = trim(base.substring(0, Math.min(base.length(), MAX_SLUG - suffix.length()))) + suffix;
            if (isFree(candidate)) return candidate;
        }
        // Mil barbearias com o mesmo nome: sufixo aleatorio em vez de laco infinito.
        return trim(base.substring(0, Math.min(base.length(), MAX_SLUG - 7))) + "-"
                + Long.toString(Math.abs(System.nanoTime()) % 1_000_000L, 36);
    }

    private boolean isFree(String slug) {
        // O slug vira o nome da instancia do WhatsApp quando o dono gera o QR: livre tambem la.
        return barbershopRepository.findBySlug(slug).isEmpty()
                && barbershopRepository.findByWhatsappInstance(slug).isEmpty();
    }

    /** Minusculas, sem acento, so letras/numeros/hifen, sem hifen nas pontas, ate {@link #MAX_SLUG}. */
    static String slugify(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " e ");
        String s = NON_SLUG.matcher(ascii).replaceAll("-");
        s = EDGE_HYPHENS.matcher(s).replaceAll("");
        return trim(s);
    }

    private static String trim(String s) {
        String cut = s.length() > MAX_SLUG ? s.substring(0, MAX_SLUG) : s;
        return EDGE_HYPHENS.matcher(cut).replaceAll("");
    }

    private static String plan(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String p = raw.trim().toUpperCase(Locale.ROOT);
        // Plano desconhecido (link antigo, digitado na mao) nao barra o cadastro: so nao e gravado.
        return PLANS.contains(p) ? p : null;
    }

    /** Tira espacos repetidos e das pontas; nulo vira vazio. */
    private static String clean(String v) {
        return v == null ? "" : v.trim().replaceAll("\\s+", " ");
    }
}

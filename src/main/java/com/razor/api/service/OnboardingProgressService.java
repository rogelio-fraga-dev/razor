package com.razor.api.service;

import com.razor.api.model.Barbershop;
import com.razor.api.model.Interaction.InteractionType;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Progresso do assistente guiado de 10 minutos (V49), sempre do tenant atual.
 *
 * <p>Fica no servidor para o dono poder parar no celular e continuar no computador. Cada passo
 * e marcado ao avancar (concluido ou pulado — os dois contam como "ja passou por aqui"); o que
 * ficou faltando de verdade continua cobrado pelo checklist de configuracao, que olha os dados.
 */
@Slf4j
@Service
public class OnboardingProgressService {

    /** Passos do assistente, na ordem da tela. Contrato com o painel (OnboardingWizard.tsx). */
    public static final List<String> STEPS =
            List.of("casa", "horario", "servicos", "equipe", "whatsapp", "teste", "site");

    /** Mensagem mais velha que isso nao conta como "o teste de agora" (as datas sao do fuso SP). */
    static final Duration TEST_WINDOW = Duration.ofMinutes(30);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final BarbershopRepository barbershopRepository;
    private final InteractionRepository interactionRepository;
    private final Clock clock;

    @Autowired
    public OnboardingProgressService(BarbershopRepository barbershopRepository,
                                     InteractionRepository interactionRepository) {
        this(barbershopRepository, interactionRepository, Clock.system(ZONE));
    }

    OnboardingProgressService(BarbershopRepository barbershopRepository,
                              InteractionRepository interactionRepository, Clock clock) {
        this.barbershopRepository = barbershopRepository;
        this.interactionRepository = interactionRepository;
        this.clock = clock;
    }

    public record Progress(String slug, String status, LocalDateTime trialEndsAt, String city, String plan,
                           List<String> steps, List<String> stepsDone, boolean completed) {
    }

    /** Teste do bot ao vivo: chegou mensagem de cliente nos ultimos minutos? O bot respondeu? */
    public record BotTest(LocalDateTime lastCustomerMessageAt, LocalDateTime lastBotMessageAt,
                          boolean received, boolean replied) {
    }

    public Progress get() {
        return toProgress(current());
    }

    /** Marca um passo como visto (concluido ou pulado). Idempotente. */
    public Progress markStep(String step) {
        if (!STEPS.contains(step)) {
            throw new IllegalArgumentException("Passo desconhecido: " + step);
        }
        Barbershop shop = current();
        Set<String> done = parse(shop.getOnboardingStepsDone());
        if (done.add(step)) {
            shop.setOnboardingStepsDone(String.join(",", done));
            shop = barbershopRepository.save(shop);
        }
        return toProgress(shop);
    }

    /** Fecha o assistente: o checklist deixa de oferecer "continuar a configuracao guiada". */
    public Progress complete() {
        Barbershop shop = current();
        if (shop.getOnboardingCompletedAt() == null) {
            shop.setOnboardingCompletedAt(LocalDateTime.now(clock));
            shop = barbershopRepository.save(shop);
            log.info("[ONBOARDING] Assistente guiado concluido pelo tenant {}", shop.getId());
        }
        return toProgress(shop);
    }

    public BotTest botTest() {
        var shop = TenantContext.getCurrent();
        LocalDateTime user = interactionRepository.findLastTimestamp(shop, InteractionType.USER);
        LocalDateTime bot = interactionRepository.findLastTimestamp(shop, InteractionType.BOT);
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(TEST_WINDOW);
        boolean received = user != null && user.isAfter(cutoff);
        boolean replied = received && bot != null && !bot.isBefore(user);
        return new BotTest(user, bot, received, replied);
    }

    private Barbershop current() {
        return barbershopRepository.findById(TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Barbearia nao encontrada."));
    }

    private Progress toProgress(Barbershop shop) {
        Set<String> done = parse(shop.getOnboardingStepsDone());
        return new Progress(shop.getSlug(), shop.getStatus(), shop.getTrialEndsAt(), shop.getCity(),
                shop.getSignupPlan(), STEPS, new ArrayList<>(done), shop.getOnboardingCompletedAt() != null);
    }

    /** CSV gravado -> passos conhecidos, na ordem gravada (passo que saiu do assistente some). */
    static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return out;
        Arrays.stream(csv.split(",")).map(String::trim).filter(STEPS::contains).forEach(out::add);
        return out;
    }
}

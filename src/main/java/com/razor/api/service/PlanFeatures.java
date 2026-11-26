package com.razor.api.service;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * O que cada plano libera (26/11, piloto). O plano é o {@code signup_plan} da barbearia; sem plano,
 * tudo liberado (contas antigas e a demo continuam como estavam).
 *
 * <p>No PILOTO ficam desligados o cartão fidelidade, o clube de assinatura e os pacotes: no teste
 * grátis não há cobrança recorrente de cliente nem benefício acumulado que depois some.
 */
@Service
@RequiredArgsConstructor
public class PlanFeatures {

    public enum Feature {
        /** Pontos por visita e cupom da cartela cheia. */
        LOYALTY,
        /** Clube de assinatura (planos e benefícios). */
        CLUB,
        /** Pacotes de cortes pagos adiantado. */
        PACKAGES
    }

    public static final String PILOT = "PILOTO";

    private final BarbershopRepository barbershopRepository;

    /** Funções desligadas para um plano. */
    public static Set<Feature> disabledFor(String plan) {
        if (plan != null && PILOT.equals(plan.trim().toUpperCase(Locale.ROOT))) {
            return EnumSet.of(Feature.LOYALTY, Feature.CLUB, Feature.PACKAGES);
        }
        return EnumSet.noneOf(Feature.class);
    }

    /** Plano da barbearia atual (do contexto da requisição/mensagem), ou nulo. */
    public String currentPlan() {
        UUID shop = TenantContext.getCurrent();
        if (shop == null) return null;
        return barbershopRepository.findById(shop).map(Barbershop::getSignupPlan).orElse(null);
    }

    public boolean enabled(Feature feature) {
        return !disabledFor(currentPlan()).contains(feature);
    }
}

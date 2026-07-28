package com.razor.api.service;

import com.razor.api.model.PlanBenefit;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.PlanBenefitRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Beneficios de plano (V27): o que o assinante ganha em cada servico/produto.
 *
 * <p>Inclui os <b>planos sugeridos</b> (Bronze/Prata/Ouro): um ponto de partida pronto que o dono
 * aplica com um clique. Nao sao criados automaticamente para ninguem — plano e preco sao decisao
 * comercial da barbearia, e materializar isso sem pedir seria presumir pelo dono.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanBenefitService {

    /** Segunda, terca e quarta — o recorte classico de plano barato em dia fraco. */
    private static final String SEG_A_QUA = "1,2,3";

    private final PlanBenefitRepository benefitRepository;
    private final SubscriptionPlanRepository planRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public List<PlanBenefit> listByPlan(UUID planId) {
        requirePlan(planId);
        return benefitRepository.findByPlanId(planId);
    }

    @Transactional
    public PlanBenefit upsert(UUID planId, PlanBenefit.TargetType type, UUID targetId,
                              BigDecimal discountPercent, Integer monthlyQuota, String weekdays) {
        requirePlan(planId);
        validate(discountPercent, monthlyQuota);

        // Um beneficio por alvo dentro do plano — o banco tem indice unico; aqui a segunda
        // gravacao vira edicao em vez de erro, que e o que a tela espera.
        PlanBenefit benefit = benefitRepository.findByPlanId(planId).stream()
                .filter(b -> b.getTargetType() == type)
                .filter(b -> java.util.Objects.equals(b.getTargetId(), targetId))
                .findFirst()
                .orElseGet(() -> PlanBenefit.builder()
                        .barbershopId(TenantContext.getCurrent())
                        .planId(planId)
                        .targetType(type)
                        .targetId(targetId)
                        .build());

        benefit.setDiscountPercent(discountPercent);
        benefit.setMonthlyQuota(monthlyQuota);
        benefit.setWeekdays(blankToNull(weekdays));
        return benefitRepository.save(benefit);
    }

    @Transactional
    public void delete(UUID planId, UUID benefitId) {
        requirePlan(planId);
        PlanBenefit benefit = benefitRepository.findById(benefitId)
                .filter(b -> planId.equals(b.getPlanId()))
                .orElseThrow(() -> new NotFoundException("Beneficio nao encontrado neste plano."));
        benefitRepository.delete(benefit);
    }

    /**
     * Cria os tres planos sugeridos com seus beneficios, a partir do catalogo de servicos do
     * tenant. Idempotente por nome: rodar de novo nao duplica.
     */
    @Transactional
    public List<SubscriptionPlan> createSuggestedPlans() {
        UUID shop = TenantContext.getCurrent();
        List<ServiceOffering> catalogo = serviceOfferingRepository.findByBarbershopIdAndActiveTrue(shop);
        UUID corte = idOfServiceLike(catalogo, "corte");
        UUID barba = idOfServiceLike(catalogo, "barba");

        List<SubscriptionPlan> criados = new ArrayList<>();

        // BRONZE — corte ilimitado nos dias fracos. Entrada barata que enche segunda a quarta.
        SubscriptionPlan bronze = upsertPlan("Bronze — Corte ilimitado (seg a qua)",
                new BigDecimal("89.90"),
                "Corte ilimitado de segunda a quarta. Ideal para quem tem horario flexivel.", 7);
        benefitFor(bronze, PlanBenefit.TargetType.SERVICO, corte, "100", null, SEG_A_QUA);
        criados.add(bronze);

        // PRATA — corte ilimitado todo dia + desconto em produto.
        SubscriptionPlan prata = upsertPlan("Prata — Corte ilimitado (todos os dias)",
                new BigDecimal("129.90"),
                "Corte ilimitado qualquer dia da semana e 10% de desconto nos produtos.", 10);
        benefitFor(prata, PlanBenefit.TargetType.SERVICO, corte, "100", null, null);
        benefitFor(prata, PlanBenefit.TargetType.PRODUTO, null, "10", null, null);
        criados.add(prata);

        // OURO — corte e barba ilimitados + descontos maiores.
        SubscriptionPlan ouro = upsertPlan("Ouro — Corte e barba ilimitados",
                new BigDecimal("189.90"),
                "Corte e barba ilimitados todos os dias, 15% nos produtos e 10% nos demais servicos.", 15);
        benefitFor(ouro, PlanBenefit.TargetType.SERVICO, corte, "100", null, null);
        if (barba != null) {
            benefitFor(ouro, PlanBenefit.TargetType.SERVICO, barba, "100", null, null);
        }
        // Beneficio geral: vale para os servicos que nao tem regra propria acima.
        benefitFor(ouro, PlanBenefit.TargetType.SERVICO, null, "10", null, null);
        benefitFor(ouro, PlanBenefit.TargetType.PRODUTO, null, "15", null, null);
        criados.add(ouro);

        log.info("[PLANOS] {} planos sugeridos criados/atualizados no tenant {}", criados.size(), shop);
        return criados;
    }

    // ---------- apoio ----------

    private SubscriptionPlan upsertPlan(String name, BigDecimal price, String description,
                                        Integer bookingWindowDays) {
        SubscriptionPlan plan = planRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .filter(p -> name.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> SubscriptionPlan.builder()
                        .barbershopId(TenantContext.getCurrent())
                        .name(name)
                        .active(true)
                        .build());
        plan.setPrice(price);
        plan.setDescription(description);
        plan.setBookingWindowDays(bookingWindowDays);
        return planRepository.save(plan);
    }

    private void benefitFor(SubscriptionPlan plan, PlanBenefit.TargetType type, UUID targetId,
                            String percent, Integer quota, String weekdays) {
        if (type == PlanBenefit.TargetType.SERVICO && targetId == null && percent.equals("100")) {
            return; // "tudo de graca" nunca e sugestao segura
        }
        upsert(plan.getId(), type, targetId, new BigDecimal(percent), quota, weekdays);
    }

    /** Acha o servico cujo nome contem o termo — o catalogo varia de barbearia para barbearia. */
    private UUID idOfServiceLike(List<ServiceOffering> catalogo, String termo) {
        return catalogo.stream()
                .filter(s -> s.getName() != null
                        && s.getName().toLowerCase(Locale.ROOT).contains(termo))
                .map(ServiceOffering::getId)
                .findFirst()
                .orElse(null);
    }

    private SubscriptionPlan requirePlan(UUID planId) {
        return Optional.ofNullable(planId)
                .flatMap(planRepository::findById)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Plano nao encontrado."));
    }

    private void validate(BigDecimal discountPercent, Integer monthlyQuota) {
        if (discountPercent == null
                || discountPercent.signum() < 0
                || discountPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("O desconto deve estar entre 0 e 100.");
        }
        if (monthlyQuota != null && monthlyQuota <= 0) {
            throw new IllegalArgumentException("A cota mensal deve ser maior que zero.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

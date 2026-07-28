package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.PlanBenefit;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.PlanBenefitRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Preco de um item para um cliente, aplicando os beneficios do plano dele (V27).
 *
 * <p>Reproduz o que o app do cliente mostra: <i>De R$ 35,00 / Por R$ 0,00</i>. Um beneficio
 * apontado para o item especifico <b>ganha</b> do beneficio geral do tipo — "Corte: 100% OFF"
 * prevalece sobre "todos os servicos: 10% OFF", que e o comportamento que o dono espera ao
 * cadastrar uma excecao.
 *
 * <p><b>Cota:</b> so e verificada para servico, contando os atendimentos do cliente no mes. Nao ha
 * como contar consumo de produto por cliente hoje (a venda nao guarda o cliente), e por isso cota
 * em beneficio de produto e ignorada — no modelo real ela nao e usada: produto ganha percentual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPricingService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final PlanBenefitRepository benefitRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    /**
     * O preco de um item para o cliente na data informada.
     *
     * @param customer cliente (pode ser {@code null} — visitante paga cheio)
     * @param basePrice preco de tabela
     */
    public PricedItem priceFor(Customer customer, PlanBenefit.TargetType type, UUID targetId,
                               String itemName, BigDecimal basePrice, LocalDate date) {
        BigDecimal original = basePrice == null ? BigDecimal.ZERO : basePrice.setScale(2, RoundingMode.HALF_UP);
        UUID planId = customer == null ? null : customer.getSubscriptionPlanId();
        if (planId == null) {
            return PricedItem.fullPrice(itemName, original);
        }

        Optional<PlanBenefit> applicable = resolveBenefit(planId, type, targetId, date);
        if (applicable.isEmpty()) {
            return PricedItem.fullPrice(itemName, original);
        }
        PlanBenefit benefit = applicable.get();

        if (!hasQuotaLeft(customer, benefit, type, itemName, date)) {
            return PricedItem.fullPrice(itemName, original);
        }

        return applyDiscount(itemName, original, benefit.getDiscountPercent());
    }

    private PricedItem applyDiscount(String name, BigDecimal original, BigDecimal percent) {
        BigDecimal discount = original.multiply(percent).divide(CEM, 2, RoundingMode.HALF_UP);
        // 100% OFF zera; nunca inverte o sinal.
        return new PricedItem(name, original, original.subtract(discount).max(BigDecimal.ZERO), percent, true);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Atendimentos do cliente no mes corrente — base da cota, lida uma vez so. */
    private List<Appointment> appointmentsThisMonth(Customer customer, LocalDate date) {
        if (customer == null || customer.getPhoneNumber() == null) return List.<Appointment>of();
        LocalDate monthStart = date.withDayOfMonth(1);
        return appointmentRepository
                .findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), customer.getPhoneNumber())
                .stream()
                .filter(a -> a.getAppointmentDate() != null)
                .filter(a -> !a.getAppointmentDate().isBefore(monthStart)
                        && !a.getAppointmentDate().isAfter(date))
                .toList();
    }

    /**
     * O catalogo de servicos ativos ja precificado para este cliente. E o que o bot usa para
     * responder "quais os precos?" — assinante ouve o preco dele, nao o de tabela.
     */
    public List<PricedItem> priceCatalogFor(Customer customer, LocalDate date) {
        List<ServiceOffering> catalogo =
                serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
        if (catalogo.isEmpty()) return List.<PricedItem>of();

        UUID planId = customer == null ? null : customer.getSubscriptionPlanId();
        if (planId == null) {
            return catalogo.stream()
                    .map(s -> PricedItem.fullPrice(s.getName(), scale(s.getPrice())))
                    .toList();
        }

        // Beneficios e historico sao lidos UMA vez para o catalogo inteiro. Chamar priceFor por
        // item faria uma consulta de beneficios (e outra de agendamentos, quando ha cota) por
        // servico — N+1 num caminho que o bot executa a cada "quais os precos?".
        List<PlanBenefit> benefits = benefitRepository.findByPlanId(planId).stream()
                .filter(b -> b.getTargetType() == PlanBenefit.TargetType.SERVICO)
                .filter(b -> b.appliesOn(date.getDayOfWeek()))
                .toList();
        List<Appointment> historicoDoMes = benefits.stream().anyMatch(b -> b.getMonthlyQuota() != null)
                ? appointmentsThisMonth(customer, date)
                : List.<Appointment>of();

        return catalogo.stream()
                .map(s -> priceWith(benefits, historicoDoMes, s.getId(), s.getName(), scale(s.getPrice())))
                .toList();
    }

    /** Precifica um item com os beneficios e o historico ja carregados. */
    private PricedItem priceWith(List<PlanBenefit> benefits, List<Appointment> historicoDoMes,
                                 UUID targetId, String name, BigDecimal original) {
        PlanBenefit benefit = benefits.stream()
                .filter(b -> targetId != null && targetId.equals(b.getTargetId()))
                .findFirst()
                .orElseGet(() -> benefits.stream()
                        .filter(b -> b.getTargetId() == null)
                        .findFirst()
                        .orElse(null));
        if (benefit == null) return PricedItem.fullPrice(name, original);

        if (benefit.getMonthlyQuota() != null) {
            long usados = historicoDoMes.stream()
                    .filter(a -> name.equalsIgnoreCase(serviceOf(a)))
                    .count();
            if (usados >= benefit.getMonthlyQuota()) return PricedItem.fullPrice(name, original);
        }
        return applyDiscount(name, original, benefit.getDiscountPercent());
    }

    /** Beneficio que vale para o item: o especifico ganha do geral; ambos respeitam o dia. */
    private Optional<PlanBenefit> resolveBenefit(UUID planId, PlanBenefit.TargetType type,
                                                 UUID targetId, LocalDate date) {
        List<PlanBenefit> benefits = benefitRepository.findByPlanId(planId).stream()
                .filter(b -> b.getTargetType() == type)
                .filter(b -> b.appliesOn(date.getDayOfWeek()))
                .toList();

        Optional<PlanBenefit> specific = benefits.stream()
                .filter(b -> targetId != null && targetId.equals(b.getTargetId()))
                .findFirst();
        if (specific.isPresent()) return specific;

        return benefits.stream().filter(b -> b.getTargetId() == null).findFirst();
    }

    /**
     * Cota do mes. Sem cota cadastrada, ilimitado. Para produto nao ha como contar consumo por
     * cliente hoje, entao a cota nao restringe.
     */
    private boolean hasQuotaLeft(Customer customer, PlanBenefit benefit,
                                 PlanBenefit.TargetType type, String itemName, LocalDate date) {
        if (benefit.getMonthlyQuota() == null) return true;
        if (type != PlanBenefit.TargetType.SERVICO) return true;
        if (customer.getPhoneNumber() == null || itemName == null) return true;

        LocalDate monthStart = date.withDayOfMonth(1);
        long usados = appointmentRepository
                .findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), customer.getPhoneNumber()).stream()
                .filter(a -> a.getAppointmentDate() != null)
                .filter(a -> !a.getAppointmentDate().isBefore(monthStart) && !a.getAppointmentDate().isAfter(date))
                .filter(a -> itemName.equalsIgnoreCase(serviceOf(a)))
                .count();
        return usados < benefit.getMonthlyQuota();
    }

    private String serviceOf(Appointment a) {
        return a.getService() == null ? "" : a.getService().trim();
    }

    /**
     * Preco de um item para um cliente: o cheio e o com desconto, como a tela mostra
     * (<i>De R$ 35,00 / Por R$ 0,00</i>).
     */
    public record PricedItem(
            String name,
            BigDecimal originalPrice,
            BigDecimal finalPrice,
            BigDecimal discountPercent,
            boolean discounted) {

        static PricedItem fullPrice(String name, BigDecimal price) {
            return new PricedItem(name, price, price, BigDecimal.ZERO, false);
        }

        /** True quando o beneficio zerou o item — o "GRATIS" da tela. */
        public boolean isFree() {
            return finalPrice.signum() == 0 && discounted;
        }
    }
}

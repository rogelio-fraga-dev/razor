package com.razor.api.service;

import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Precificacao data-driven (Fase 1): o valor de cada servico vem do catalogo
 * {@code services} do tenant atual, e nao mais de numeros fixos no codigo.
 *
 * <p>Para manter compatibilidade com os dados legados — em que {@code Appointment.service}
 * e uma string livre gerada pela IA ("Corte", "Combo", "Corte + Barba"...) — o servico e
 * primeiro <b>categorizado</b> por palavra-chave e o preco daquela categoria e resolvido no
 * catalogo. Quando o catalogo nao tem a categoria (ou esta vazio, como nos testes unitarios),
 * caem-se nos padroes historicos (combo=60, barba=30, demais=35), preservando o comportamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    /** Categoria de servico usada tanto para o preco quanto para as metricas do financeiro. */
    public enum ServiceCategory {
        COMBO, BARBA, SIMPLES
    }

    private static final Map<ServiceCategory, BigDecimal> DEFAULT_PRICES = Map.of(
            ServiceCategory.COMBO, new BigDecimal("60.00"),
            ServiceCategory.BARBA, new BigDecimal("30.00"),
            ServiceCategory.SIMPLES, new BigDecimal("35.00"));

    private final ServiceOfferingRepository serviceOfferingRepository;

    /**
     * Classifica um servico por palavra-chave. A ordem importa: "Corte + Barba (Combo)" contem
     * tanto "barba" quanto "combo" e deve cair em COMBO; "Corte + Barba" (sem "combo") cai em BARBA.
     */
    public ServiceCategory categorize(String serviceName) {
        String s = serviceName == null ? "" : serviceName.toLowerCase();
        if (s.contains("combo")) return ServiceCategory.COMBO;
        if (s.contains("barba")) return ServiceCategory.BARBA;
        return ServiceCategory.SIMPLES;
    }

    /**
     * Tabela categoria -> preco resolvida numa unica consulta ao catalogo do tenant.
     * Sempre contem as tres categorias: o catalogo sobrescreve os padroes historicos onde existir.
     * Ideal para loops de relatorio (evita N+1).
     */
    public Map<ServiceCategory, BigDecimal> resolvePrices() {
        Map<ServiceCategory, BigDecimal> prices = new EnumMap<>(ServiceCategory.class);
        List<ServiceOffering> catalog =
                serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
        if (catalog != null) {
            for (ServiceOffering offering : catalog) {
                if (offering.getPrice() == null) continue;
                // Se duas ofertas caem na mesma categoria, a primeira (ativa mais estavel) prevalece.
                prices.putIfAbsent(categorize(offering.getName()), offering.getPrice());
            }
        }
        // Categorias sem oferta no catalogo caem no padrao historico.
        DEFAULT_PRICES.forEach(prices::putIfAbsent);
        return prices;
    }

    /** Preco de um servico (string livre) — conveniencia para uso pontual (uma consulta). */
    public BigDecimal priceFor(String serviceName) {
        return resolvePrices().get(categorize(serviceName));
    }
}

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
 *
 * <p>Alem do preco, resolve tambem a <b>duracao</b> do servico pela mesma logica de categoria —
 * base para a disponibilidade de horarios respeitar servicos mais longos (ex.: combo de 60min
 * ocupa dois slots de 30min).
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

    /** Duracoes padrao (minutos), alinhadas ao seed V5: combo=60, barba=30, corte=30. */
    private static final Map<ServiceCategory, Integer> DEFAULT_DURATIONS = Map.of(
            ServiceCategory.COMBO, 60,
            ServiceCategory.BARBA, 30,
            ServiceCategory.SIMPLES, 30);

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

    /**
     * Preco de um agendamento (string livre, ex.: "Corte, Barba") — conveniencia para uso pontual
     * (uma consulta). Soma os servicos do catalogo citados; ver {@link Quoter#quote}.
     */
    public BigDecimal priceFor(String serviceName) {
        return quoter().price(serviceName);
    }

    /** Orcamento (itens, total e duracao) de um agendamento — uma consulta ao catalogo. */
    public Quote quote(String serviceText) {
        return quoter().quote(serviceText);
    }

    /**
     * Retrato do catalogo do tenant atual para precificar muitos agendamentos com UMA consulta
     * (relatorios, card da IA, comissao, ocupacao). E o calculo unico de preco e duracao do
     * agendamento: bot, painel e comanda usam este mesmo caminho.
     */
    public Quoter quoter() {
        return Quoter.of(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent()));
    }

    // ------------------------------------------------------------------ calculo unico (V46)

    /** Um servico do orcamento. {@code serviceId} nulo = preco/duracao do padrao historico. */
    public record QuoteLine(java.util.UUID serviceId, String name, BigDecimal price, int durationMinutes) {
    }

    /**
     * Orcamento de um agendamento.
     *
     * @param fromCatalog false = nada casou com o catalogo e valeu o padrao historico por categoria
     */
    public record Quote(List<QuoteLine> lines, BigDecimal total, int durationMinutes, boolean fromCatalog) {
        /** Mais de um servico: vale mostrar item a item. */
        public boolean multiple() {
            return lines.size() > 1;
        }
    }

    /**
     * Combo do catalogo que cobre servicos escolhidos separadamente, mais barato que a soma.
     *
     * @param covered servicos do pedido que o combo substitui
     * @param savings quanto o cliente economiza (soma dos cobertos menos o preco do combo)
     */
    public record ComboSuggestion(QuoteLine combo, List<QuoteLine> covered, BigDecimal savings) {
    }

    /**
     * Precificador sobre um retrato do catalogo. Regra para o texto guardado em
     * {@code appointments.service} (livre: "Corte", "Corte + Barba", "Corte de Cabelo, Barba,
     * Sobrancelha"...):
     * <ol>
     *   <li>nome exato de um servico do catalogo (inclusive um combo: vale o preco DO COMBO, nao a
     *       soma das partes);</li>
     *   <li>senao, cada servico citado por inteiro no texto ({@link ServiceCapability#mentioned}:
     *       quando um nome contem outro, fica o mais longo — "Corte e Barba" nao conta "Corte" de
     *       novo); soma precos e duracoes;</li>
     *   <li>senao, abreviacao ("corte" vira o primeiro servico cujo nome contem "corte");</li>
     *   <li>senao, o padrao historico por categoria (combo/barba/simples), como antes.</li>
     * </ol>
     */
    public static final class Quoter {
        private final List<ServiceOffering> catalog;
        private final Map<ServiceCategory, BigDecimal> legacyPrices;
        private final Map<ServiceCategory, Integer> legacyDurations;

        private Quoter(List<ServiceOffering> catalog, Map<ServiceCategory, BigDecimal> legacyPrices,
                       Map<ServiceCategory, Integer> legacyDurations) {
            this.catalog = catalog;
            this.legacyPrices = legacyPrices;
            this.legacyDurations = legacyDurations;
        }

        public static Quoter of(List<ServiceOffering> rawCatalog) {
            List<ServiceOffering> catalog = rawCatalog == null ? List.of() : rawCatalog.stream()
                    .filter(o -> o != null && o.getName() != null && !o.getName().isBlank())
                    .toList();
            Map<ServiceCategory, BigDecimal> prices = new EnumMap<>(ServiceCategory.class);
            Map<ServiceCategory, Integer> durations = new EnumMap<>(ServiceCategory.class);
            for (ServiceOffering o : catalog) {
                if (o.getPrice() != null) prices.putIfAbsent(categorizeStatic(o.getName()), o.getPrice());
                if (o.getDurationMinutes() != null) durations.putIfAbsent(categorizeStatic(o.getName()), o.getDurationMinutes());
            }
            return new Quoter(catalog, prices, durations);
        }

        /** Sem catalogo: so o padrao historico por categoria (tabelas dadas sobrescrevem). */
        public static Quoter legacy(Map<ServiceCategory, BigDecimal> prices, Map<ServiceCategory, Integer> durations) {
            return new Quoter(List.of(),
                    prices == null ? Map.of() : prices,
                    durations == null ? Map.of() : durations);
        }

        public BigDecimal price(String serviceText) {
            return quote(serviceText).total();
        }

        public int duration(String serviceText) {
            return quote(serviceText).durationMinutes();
        }

        public Quote quote(String serviceText) {
            List<ServiceOffering> itens = match(serviceText);
            if (itens.isEmpty()) {
                ServiceCategory cat = categorizeStatic(serviceText);
                BigDecimal p = legacyPrices.getOrDefault(cat, DEFAULT_PRICES.get(cat));
                int d = legacyDurations.getOrDefault(cat, DEFAULT_DURATIONS.get(cat));
                String nome = serviceText == null || serviceText.isBlank() ? "Serviço" : serviceText.trim();
                return new Quote(List.of(new QuoteLine(null, nome, p, d)), p, d, false);
            }
            List<QuoteLine> lines = itens.stream().map(Quoter::line).toList();
            BigDecimal total = lines.stream().map(QuoteLine::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            int dur = lines.stream().mapToInt(QuoteLine::durationMinutes).sum();
            return new Quote(lines, total, dur, true);
        }

        /**
         * O combo do catalogo que mais economiza para os servicos pedidos, se houver. Combo = servico
         * cujo nome cita dois ou mais outros servicos do catalogo ("Corte + Barba", "Combo Corte e
         * Barba") e custa menos que a soma deles. So sugere quando TODOS os servicos do combo foram
         * pedidos; os demais continuam no pedido.
         */
        public java.util.Optional<ComboSuggestion> comboFor(String serviceText) {
            List<ServiceOffering> pedidos = match(serviceText);
            if (pedidos.size() < 2) return java.util.Optional.empty();
            java.util.Set<java.util.UUID> ids = pedidos.stream().map(ServiceOffering::getId)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            ComboSuggestion melhor = null;
            for (ServiceOffering c : catalog) {
                if (c.getId() == null || ids.contains(c.getId()) || c.getPrice() == null) continue;
                List<ServiceOffering> partes = ServiceCapability.mentioned(c.getName(),
                        catalog.stream().filter(o -> o != c).toList());
                if (partes.size() < 2 || !partes.stream().allMatch(p -> ids.contains(p.getId()))) continue;
                List<QuoteLine> cobertos = partes.stream().map(Quoter::line).toList();
                BigDecimal soma = cobertos.stream().map(QuoteLine::price).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal economia = soma.subtract(c.getPrice());
                if (economia.signum() <= 0) continue;
                if (melhor == null || economia.compareTo(melhor.savings()) > 0) {
                    melhor = new ComboSuggestion(line(c), cobertos, economia);
                }
            }
            return java.util.Optional.ofNullable(melhor);
        }

        private List<ServiceOffering> match(String serviceText) {
            String wanted = ServiceCapability.normalize(serviceText);
            if (wanted.isEmpty() || catalog.isEmpty()) return List.of();
            for (ServiceOffering o : catalog) {
                if (ServiceCapability.normalize(o.getName()).equals(wanted)) return List.of(o);
            }
            List<ServiceOffering> comId = catalog.stream().filter(o -> o.getId() != null).toList();
            List<ServiceOffering> citados = ServiceCapability.mentioned(serviceText, comId);
            if (!citados.isEmpty()) return citados;
            return catalog.stream()
                    .filter(o -> ServiceCapability.containsWordPublic(ServiceCapability.normalize(o.getName()), wanted))
                    .findFirst().map(List::of).orElse(List.of());
        }

        private static QuoteLine line(ServiceOffering o) {
            return new QuoteLine(o.getId(), o.getName(),
                    o.getPrice() != null ? o.getPrice() : BigDecimal.ZERO,
                    o.getDurationMinutes() != null && o.getDurationMinutes() > 0 ? o.getDurationMinutes() : 30);
        }
    }

    /** {@link #categorize} sem instancia (usado pelo {@link Quoter}). */
    static ServiceCategory categorizeStatic(String serviceName) {
        String s = serviceName == null ? "" : serviceName.toLowerCase();
        if (s.contains("combo")) return ServiceCategory.COMBO;
        if (s.contains("barba")) return ServiceCategory.BARBA;
        return ServiceCategory.SIMPLES;
    }

    /**
     * Tabela categoria -> duracao (minutos) resolvida numa unica consulta ao catalogo do tenant.
     * Sempre contem as tres categorias: o catalogo sobrescreve os padroes onde existir.
     */
    public Map<ServiceCategory, Integer> resolveDurations() {
        Map<ServiceCategory, Integer> durations = new EnumMap<>(ServiceCategory.class);
        List<ServiceOffering> catalog =
                serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
        if (catalog != null) {
            for (ServiceOffering offering : catalog) {
                if (offering.getDurationMinutes() == null) continue;
                durations.putIfAbsent(categorize(offering.getName()), offering.getDurationMinutes());
            }
        }
        DEFAULT_DURATIONS.forEach(durations::putIfAbsent);
        return durations;
    }
}

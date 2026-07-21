package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.ServiceOffering;
import com.razor.api.service.PricingService.ServiceCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link PricingService}: categorizacao por palavra-chave e resolucao de preco
 * a partir do catalogo com fallback nos padroes historicos — tudo com o repositorio mockado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    @Mock private com.razor.api.repository.ServiceOfferingRepository serviceOfferingRepository;

    @InjectMocks private PricingService pricingService;

    private ServiceOffering offering(String name, String price) {
        return offering(name, price, 30);
    }

    private ServiceOffering offering(String name, String price, int durationMinutes) {
        return ServiceOffering.builder()
                .id(UUID.randomUUID())
                .name(name)
                .price(new BigDecimal(price))
                .durationMinutes(durationMinutes)
                .active(true)
                .build();
    }

    // ---------- categorizacao ----------

    @Test
    @DisplayName("categorize: 'Corte + Barba (Combo)' cai em COMBO mesmo contendo 'barba'")
    void categorize_comboWinsOverBarba() {
        assertThat(pricingService.categorize("Corte + Barba (Combo)")).isEqualTo(ServiceCategory.COMBO);
    }

    @Test
    @DisplayName("categorize: 'Corte + Barba' (sem 'combo') cai em BARBA")
    void categorize_barba() {
        assertThat(pricingService.categorize("Corte + Barba")).isEqualTo(ServiceCategory.BARBA);
    }

    @Test
    @DisplayName("categorize: 'Corte de Cabelo' cai em SIMPLES")
    void categorize_simples() {
        assertThat(pricingService.categorize("Corte de Cabelo")).isEqualTo(ServiceCategory.SIMPLES);
    }

    @Test
    @DisplayName("categorize: nome nulo cai em SIMPLES (nunca lanca)")
    void categorize_nullIsSimples() {
        assertThat(pricingService.categorize(null)).isEqualTo(ServiceCategory.SIMPLES);
    }

    // ---------- resolucao de preco ----------

    @Test
    @DisplayName("catalogo vazio: cai nos padroes historicos (combo=60, barba=30, simples=35)")
    void resolvePrices_emptyCatalog_usesDefaults() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());

        var precos = pricingService.resolvePrices();

        assertThat(precos.get(ServiceCategory.COMBO)).isEqualByComparingTo("60.00");
        assertThat(precos.get(ServiceCategory.BARBA)).isEqualByComparingTo("30.00");
        assertThat(precos.get(ServiceCategory.SIMPLES)).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("repositorio nulo: nao quebra, mantem os padroes")
    void resolvePrices_nullCatalog_usesDefaults() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(null);

        assertThat(pricingService.priceFor("Corte")).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("catalogo do tenant sobrescreve os padroes por categoria")
    void resolvePrices_catalogOverridesDefaults() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte de Cabelo", "45.00"),
                offering("Barba", "25.00"),
                offering("Corte + Barba (Combo)", "65.00")));

        var precos = pricingService.resolvePrices();

        assertThat(precos.get(ServiceCategory.SIMPLES)).isEqualByComparingTo("45.00");
        assertThat(precos.get(ServiceCategory.BARBA)).isEqualByComparingTo("25.00");
        assertThat(precos.get(ServiceCategory.COMBO)).isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("priceFor resolve preco do catalogo pela categoria do servico livre")
    void priceFor_usesCatalogByCategory() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte de Cabelo", "45.00")));

        assertThat(pricingService.priceFor("Corte")).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("categoria sem oferta no catalogo mantem o padrao historico")
    void resolvePrices_partialCatalog_keepsDefaultForMissing() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Barba", "25.00")));

        var precos = pricingService.resolvePrices();

        assertThat(precos.get(ServiceCategory.BARBA)).isEqualByComparingTo("25.00");
        assertThat(precos.get(ServiceCategory.COMBO)).isEqualByComparingTo("60.00");   // padrao
        assertThat(precos.get(ServiceCategory.SIMPLES)).isEqualByComparingTo("35.00"); // padrao
    }

    // ---------- duracao ----------

    @Test
    @DisplayName("catalogo vazio: duracoes padrao (combo=60, barba=30, simples=30)")
    void resolveDurations_emptyCatalog_usesDefaults() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());

        var duracoes = pricingService.resolveDurations();

        assertThat(duracoes.get(ServiceCategory.COMBO)).isEqualTo(60);
        assertThat(duracoes.get(ServiceCategory.BARBA)).isEqualTo(30);
        assertThat(duracoes.get(ServiceCategory.SIMPLES)).isEqualTo(30);
    }

    @Test
    @DisplayName("catalogo do tenant sobrescreve a duracao por categoria")
    void resolveDurations_catalogOverrides() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte + Barba (Combo)", "65.00", 90),
                offering("Corte de Cabelo", "45.00", 45)));

        var duracoes = pricingService.resolveDurations();

        assertThat(duracoes.get(ServiceCategory.COMBO)).isEqualTo(90);
        assertThat(duracoes.get(ServiceCategory.SIMPLES)).isEqualTo(45);
        assertThat(duracoes.get(ServiceCategory.BARBA)).isEqualTo(30); // padrao (sem oferta)
    }
}

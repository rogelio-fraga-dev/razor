package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * O prompt da IA precisa falar SEMPRE os dados do tenant atual. Antes, nome da barbearia,
 * precos e horario estavam fixos no codigo — toda barbearia nova teria um bot dizendo
 * "Barbearia Ficticia" e cobrando R$ 35/30/60 de outra loja.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAIServiceTest {

    @Mock private OpenAiChatModel chatModel;
    @Mock private RazorProperties properties;
    @Mock private AgendaService agendaService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private PlanPricingService planPricingService;

    @InjectMocks private OpenAIService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ServiceOffering offering(String name, String price, int minutes) {
        return ServiceOffering.builder()
                .name(name).price(new BigDecimal(price)).durationMinutes(minutes).active(true).build();
    }

    @Test
    @DisplayName("tabela de serviços do prompt vem do catálogo do tenant (preço e duração reais)")
    void serviceTable_comesFromTenantCatalog() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte Navalhado", "45.00", 45),
                offering("Barba Terapia", "55.00", 40)));

        String table = service.buildServiceTable(null);

        assertThat(table).contains("Corte Navalhado").contains("45,00").contains("45 min");
        assertThat(table).contains("Barba Terapia").contains("55,00").contains("40 min");
        // nao pode recitar a tabela antiga que estava cravada no codigo
        assertThat(table).doesNotContain("35,00").doesNotContain("60,00");
    }

    @Test
    @DisplayName("catálogo vazio não inventa preços — orienta o cliente a aguardar")
    void serviceTable_emptyCatalog_doesNotInventPrices() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());

        String table = service.buildServiceTable(null);

        assertThat(table).doesNotContain("R$");
        assertThat(table).contains("catálogo não cadastrado");
    }

    @Test
    @DisplayName("funcionamento usa o expediente configurado pelo dono")
    void openingHours_useTenantConfig() {
        // A frase dos dias é derivada dos dias estruturados (V32) — antes era texto livre que
        // podia dizer uma coisa enquanto a agenda fazia outra.
        var cfg = new BarbershopSettingsService.BotSettings(
                "", "", "", null, "",
                "Barbearia do Diego", null, null, null,
                "08:30", "20:00",
                java.util.EnumSet.range(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.SUNDAY),
                null, null, null, null, null, null);

        assertThat(service.buildOpeningHours(cfg)).isEqualTo("Ter a Dom, das 08:30 às 20:00");
    }

    @Test
    @DisplayName("sem configuração, o expediente cai no padrão histórico (09:00-19:00)")
    void openingHours_fallBackToDefaults() {
        var cfg = new BarbershopSettingsService.BotSettings("", "", "", null, "");

        assertThat(service.buildOpeningHours(cfg)).isEqualTo("Seg a Sáb, das 09:00 às 19:00");
    }
    // ==========================================================================================
    // Preco do assinante no prompt da IA
    // ==========================================================================================

    @Test
    @DisplayName("assinante recebe o preco COM desconto na tabela que vai para a IA")
    void serviceTable_subscriber_getsPlanPrice() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte", "45.00", 30)));
        when(planPricingService.priceCatalogFor(any(), any())).thenReturn(List.of(
                new com.razor.api.service.PlanPricingService.PricedItem(
                        "Corte", new BigDecimal("45.00"), new BigDecimal("22.50"), new BigDecimal("50"), true)));

        String table = service.buildServiceTable(
                com.razor.api.model.Customer.builder().phoneNumber("5511999").build());

        // O preco cheio vai junto de proposito: e o que permite a IA dizer "de X por Y" e o
        // cliente PERCEBER o valor do plano que ele paga todo mes.
        assertThat(table).contains("22,50").contains("45,00").contains("plano");
    }

    @Test
    @DisplayName("servico gratis pelo plano aparece como GRATIS, nao como R-e,00")
    void serviceTable_freeByPlan() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte", "45.00", 30)));
        when(planPricingService.priceCatalogFor(any(), any())).thenReturn(List.of(
                new com.razor.api.service.PlanPricingService.PricedItem(
                        "Corte", new BigDecimal("45.00"), BigDecimal.ZERO, new BigDecimal("100"), true)));

        String table = service.buildServiceTable(
                com.razor.api.model.Customer.builder().phoneNumber("5511999").build());

        assertThat(table).contains("GRÁTIS").contains("45,00");
    }

    @Test
    @DisplayName("cliente sem plano continua vendo o preco cheio")
    void serviceTable_noPlan_fullPrice() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte", "45.00", 30)));
        when(planPricingService.priceCatalogFor(any(), any())).thenReturn(List.of(
                new com.razor.api.service.PlanPricingService.PricedItem(
                        "Corte", new BigDecimal("45.00"), new BigDecimal("45.00"), BigDecimal.ZERO, false)));

        String table = service.buildServiceTable(
                com.razor.api.model.Customer.builder().phoneNumber("5511999").build());

        assertThat(table).contains("45,00").doesNotContain("plano");
    }
}

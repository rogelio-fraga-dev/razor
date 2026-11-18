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
    @Mock private PackageService packageService;

    @InjectMocks private OpenAIService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("pacote pré-pago (V34): a IA sabe quantos usos o cliente ainda tem")
    void serviceTable_mentionsPackageBalance() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                offering("Corte", "40.00", 30)));
        com.razor.api.model.Customer cliente = com.razor.api.model.Customer.builder()
                .id(java.util.UUID.randomUUID()).phoneNumber("5511988887777").build();
        when(packageService.usableFor(cliente)).thenReturn(List.of(com.razor.api.model.CustomerPackage.builder()
                .packageName("10 cortes").serviceName("Corte").remainingUses(7)
                .expiresAt(java.time.LocalDate.of(2026, 12, 31)).build()));

        String table = service.buildServiceTable(cliente);

        assertThat(table).contains("PACOTE PRÉ-PAGO").contains("10 cortes").contains("7 usos restantes")
                .contains("31/12/2026");
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

    // ==========================================================================================
    // Historico da conversa: papel vem do TIPO, em ordem cronologica
    // ==========================================================================================

    private static com.razor.api.model.Interaction interaction(
            com.razor.api.model.Interaction.InteractionType type, String content, int minute) {
        return com.razor.api.model.Interaction.builder().type(type).content(content)
                .timestamp(java.time.LocalDateTime.of(2026, 11, 17, 10, minute)).build();
    }

    @Test
    @DisplayName("historico DESC com 10 itens vira chat cronologico com papeis pelo tipo")
    void history_rolesComeFromType_notFromIndexParity() {
        var U = com.razor.api.model.Interaction.InteractionType.USER;
        var B = com.razor.api.model.Interaction.InteractionType.BOT;
        // Janela de 10 itens, do mais recente para o mais antigo (como o repositorio devolve).
        // Comeca (cronologicamente) por uma fala do BOT — exatamente o caso que invertia tudo.
        List<com.razor.api.model.Interaction> desc = new java.util.ArrayList<>();
        for (int m = 9; m >= 0; m--) {
            boolean isUser = m % 2 == 1;
            desc.add(interaction(isUser ? U : B, (isUser ? "cliente " : "bot ") + m, m));
        }

        var msgs = OpenAIService.buildHistoryMessages(desc, "cliente 9");

        assertThat(msgs).hasSize(10);
        assertThat(msgs.get(0)).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        assertThat(((dev.langchain4j.data.message.AiMessage) msgs.get(0)).text()).isEqualTo("bot 0");
        for (var msg : msgs) {
            if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
                assertThat(um.singleText()).startsWith("cliente");
            } else {
                assertThat(((dev.langchain4j.data.message.AiMessage) msg).text()).startsWith("bot");
            }
        }
        // A mensagem atual do cliente e a ULTIMA e e do usuario, nao do assistente.
        assertThat(msgs.get(9)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((dev.langchain4j.data.message.UserMessage) msgs.get(9)).singleText()).isEqualTo("cliente 9");
    }

    @Test
    @DisplayName("mensagem traduzida do menu substitui a ultima fala do cliente (sem duplicar)")
    void history_translatedPromptReplacesLastUserMessage() {
        var U = com.razor.api.model.Interaction.InteractionType.USER;
        var B = com.razor.api.model.Interaction.InteractionType.BOT;
        var desc = List.of(interaction(U, "1", 2), interaction(B, "menu", 1), interaction(U, "oi", 0));

        var msgs = OpenAIService.buildHistoryMessages(desc, "Quero agendar um horário.");

        assertThat(msgs).hasSize(3);
        assertThat(((dev.langchain4j.data.message.UserMessage) msgs.get(2)).singleText())
                .isEqualTo("Quero agendar um horário.");
    }

    @Test
    @DisplayName("sem historico, so a mensagem atual vai como fala do cliente")
    void history_empty_onlyCurrentMessage() {
        var msgs = OpenAIService.buildHistoryMessages(List.of(), "oi");

        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
    }

    @Test
    @DisplayName("falha da IA vira AiUnavailableException, nunca um texto de erro como resposta")
    void processCustomerMessage_aiFailure_throws() {
        when(agendaService.getHorariosDisponiveisDoDia(any())).thenReturn("10:00");
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "", "", null, ""));
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());
        when(chatModel.generate(org.mockito.ArgumentMatchers.<List<dev.langchain4j.data.message.ChatMessage>>any()))
                .thenThrow(new RuntimeException("429 quota"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.processCustomerMessage("oi", List.of(), "livre", 0, "Joao", null))
                .isInstanceOf(AiUnavailableException.class);
    }
}

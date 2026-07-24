package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.Interaction;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do fluxo do CLIENTE (Fase 0.4). Exercita o roteamento por menu numerico,
 * o despacho para a IA (via tag) e a maquina de estados (motivo de atendimento e
 * pagamento) dirigindo o servico por chamadas sequenciais na mesma instancia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerConversationServiceTest {

    private static final String PHONE = "5511999990000";

    @Mock private CustomerService customerService;
    @Mock private CustomerRepository customerRepository;
    @Mock private InteractionRepository interactionRepository;
    @Mock private OpenAIService openAIService;
    @Mock private AgendaService agendaService;
    @Mock private RazorProperties properties;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private WhatsAppMessenger messenger;
    @Mock private EvolutionMediaService evolutionMediaService;
    @Mock private ProfessionalService professionalService;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private BarbershopSettingsService barbershopSettingsService;

    // Store REAL (nao mock): a maquina de estados da conversa precisa gravar/ler de verdade.
    @org.mockito.Spy
    private com.razor.api.state.StateStore stateStore =
            new com.razor.api.state.InMemoryStateStore(new com.fasterxml.jackson.databind.ObjectMapper());

    @InjectMocks private CustomerConversationService service;

    /** Config do tenant com a chave PIX preenchida (caso normal de uma barbearia configurada). */
    private void givenPixConfigured(String pixKey) {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(
                        "", "", "", null, "",
                        "Barbearia Teste", pixKey, null, null,
                        "09:00", "19:00", "Seg a Sáb",
                        null, null, null, null, null, null));
    }

    private void givenBotSettings(String address, String mapsUrl, String servicesText, String instagram) {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(address, mapsUrl, servicesText, null, instagram));
    }

    // ---------- helpers ----------

    private Customer givenExistingCustomer() {
        Customer c = Customer.builder()
                .id(UUID.randomUUID())
                .phoneNumber(PHONE)
                .name("João")
                .fidelityPoints(0)
                .build();
        when(customerService.findOrCreateCustomer(eq(PHONE), any())).thenReturn(c);
        when(interactionRepository.findRecentInteractionsByCustomerId(c.getId())).thenReturn(List.of());
        return c;
    }

    private EvolutionWebhookDTO textWebhook(String text) {
        EvolutionWebhookDTO w = org.mockito.Mockito.mock(EvolutionWebhookDTO.class);
        EvolutionWebhookDTO.DataDTO data = org.mockito.Mockito.mock(EvolutionWebhookDTO.DataDTO.class);
        EvolutionWebhookDTO.KeyDTO key = org.mockito.Mockito.mock(EvolutionWebhookDTO.KeyDTO.class);
        when(w.hasSticker()).thenReturn(false);
        when(w.hasAudio()).thenReturn(false);
        when(w.getMessageText()).thenReturn(text);
        when(w.getData()).thenReturn(data);
        when(data.getPushName()).thenReturn("João");
        when(data.getKey()).thenReturn(key);
        when(key.getId()).thenReturn("msg-" + Math.abs(text.hashCode()));
        return w;
    }

    private String captureClientMessage() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(messenger).send(eq(PHONE), cap.capture(), eq("CLIENTE"));
        return cap.getValue();
    }

    /** Última mensagem enviada ao cliente (útil em fluxos com múltiplos envios sequenciais). */
    private String lastClientMessage() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(messenger, org.mockito.Mockito.atLeastOnce()).send(eq(PHONE), cap.capture(), eq("CLIENTE"));
        return cap.getAllValues().get(cap.getAllValues().size() - 1);
    }

    // ---------- testes ----------

    @Test
    @DisplayName("sticker e ignorado sem criar cliente nem enviar nada")
    void sticker_isIgnored() {
        EvolutionWebhookDTO w = org.mockito.Mockito.mock(EvolutionWebhookDTO.class);
        when(w.hasSticker()).thenReturn(true);

        service.processCustomerMessage(w, PHONE);

        verifyNoInteractions(customerService, messenger);
        verify(interactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("'menu' envia o menu textual do cliente")
    void menuCommand_sendsMenu() {
        givenExistingCustomer();

        service.processCustomerMessage(textWebhook("menu"), PHONE);

        assertThat(captureClientMessage()).isEqualTo(MenuOptions.getMenuAsText(0));
    }

    @Test
    @DisplayName("opcao 4 envia endereco e link do mapa")
    void option4_sendsAddress() {
        givenExistingCustomer();
        givenBotSettings("Rua dos Testes, 123", "http://maps.example/xyz", "", "");

        service.processCustomerMessage(textWebhook("4"), PHONE);

        assertThat(captureClientMessage())
                .contains("Rua dos Testes, 123")
                .contains("http://maps.example/xyz");
    }

    @Test
    @DisplayName("opcao 5 envia a tabela de servicos")
    void option5_sendsServices() {
        givenExistingCustomer();
        givenBotSettings("", "", "TABELA DE PRECOS", "");

        service.processCustomerMessage(textWebhook("5"), PHONE);

        assertThat(captureClientMessage()).isEqualTo("TABELA DE PRECOS");
    }

    @Test
    @DisplayName("opcao 2 sem agendamentos informa que nao ha nenhum ativo")
    void option2_noAppointments() {
        givenExistingCustomer();
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of());

        service.processCustomerMessage(textWebhook("2"), PHONE);

        assertThat(captureClientMessage()).contains("não possui nenhum agendamento");
    }

    @Test
    @DisplayName("opcao 2 com um agendamento lista servico, data e hora")
    void option2_withOneAppointment() {
        givenExistingCustomer();
        Appointment ag = Appointment.builder()
                .service("Corte")
                .appointmentDate(LocalDate.of(2026, 12, 20))
                .appointmentTime(LocalTime.of(10, 0))
                .build();
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of(ag));

        service.processCustomerMessage(textWebhook("2"), PHONE);

        assertThat(captureClientMessage())
                .contains("Seus Agendamentos")
                .contains("Corte")
                .contains("20/12")
                .contains("10:00");
    }

    @Test
    @DisplayName("'cancelar meu horario' com um agendamento cancela e confirma")
    void cancelSingleAppointment() {
        givenExistingCustomer();
        Appointment ag = Appointment.builder()
                .service("Corte")
                .appointmentDate(LocalDate.of(2026, 12, 20))
                .appointmentTime(LocalTime.of(10, 0))
                .build();
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of(ag));
        when(agendaService.cancelarAgendamentoExato(ag)).thenReturn(true);
        when(waitlistRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());

        service.processCustomerMessage(textWebhook("cancelar meu horario"), PHONE);

        verify(agendaService).cancelarAgendamentoExato(ag);
        assertThat(captureClientMessage()).contains("cancelado com sucesso");
    }

    @Test
    @DisplayName("opcao 8 pede o motivo e, na resposta, pausa o bot e avisa o admin")
    void atendente_setsMotivoState_thenNotifiesAdmin() {
        givenExistingCustomer();
        when(tenantAdminService.adminPhones()).thenReturn(List.of("5511000000000"));

        // 1a mensagem: pede o motivo
        service.processCustomerMessage(textWebhook("8"), PHONE);
        assertThat(captureClientMessage()).contains("atendimento humano");

        // 2a mensagem: o motivo em si
        service.processCustomerMessage(textWebhook("preciso remarcar urgente"), PHONE);

        verify(customerService).pauseCustomer(PHONE, 15);
        verify(agendaService).agendarNotificacaoRetorno(PHONE, 15);
        verify(messenger).send(eq("5511000000000"), argThat(m -> m.contains("ATENDIMENTO")), eq("ADMIN"));
    }

    @Test
    @DisplayName("agendamento via IA entra em ESPERANDO_PAGAMENTO e o '1' confirma PIX")
    void aiNewAppointment_thenPixPayment() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA()).thenReturn("");
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");

        // 1a mensagem: a IA devolve a tag de novo agendamento
        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        verify(agendaService).adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        // 2a mensagem: cliente escolhe PIX -> status PIX salvo e a chave DO TENANT enviada
        Appointment ag = Appointment.builder().customerPhone(PHONE).build();
        when(agendaService.getAgendamentoCliente(PHONE)).thenReturn(ag);
        givenPixConfigured("pix@barbeariateste.com.br");

        service.processCustomerMessage(textWebhook("1"), PHONE);

        verify(appointmentRepository).save(argThat(a -> a.getPaymentStatus() == PaymentStatus.PIX));
        verify(messenger).send(eq(PHONE),
                argThat(m -> m.contains("CHAVE PIX") && m.contains("pix@barbeariateste.com.br")), eq("CLIENTE"));
    }

    @Test
    @DisplayName("sem PIX configurado, o bot NÃO inventa chave — orienta pagamento presencial")
    void pixPayment_withoutConfiguredKey_doesNotInventKey() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA()).thenReturn("");
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");

        // entra em ESPERANDO_PAGAMENTO pelo fluxo real
        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        Appointment ag = Appointment.builder().customerPhone(PHONE).build();
        when(agendaService.getAgendamentoCliente(PHONE)).thenReturn(ag);
        givenBotSettings("", "", "", ""); // tenant sem pixKey

        service.processCustomerMessage(textWebhook("1"), PHONE);

        verify(messenger).send(eq(PHONE),
                argThat(m -> m.contains("PIX ainda não está disponível")), eq("CLIENTE"));
        // e em nenhuma mensagem aparece a antiga chave falsa hardcoded
        verify(messenger, never()).send(eq(PHONE), argThat(m -> m.contains("12.345.678")), any());
    }

    // ---------- agenda multiprofissional (escolha explícita) ----------

    private com.razor.api.model.Professional prof(String name) {
        return com.razor.api.model.Professional.builder()
                .id(UUID.randomUUID()).name(name).active(true).build();
    }

    private void givenAiReturnsAgendamentoTag() {
        when(agendaService.getHorariosOcupadosParaIA()).thenReturn("");
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");
    }

    @Test
    @DisplayName("com 2+ barbeiros, o agendamento via IA pergunta com qual profissional e ainda NÃO reserva")
    void aiNewAppointment_multipleProfessionals_asksChoice() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(professionalService.listActive()).thenReturn(List.of(prof("Rafael"), prof("Bruno")));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        assertThat(captureClientMessage()).contains("Rafael").contains("Bruno").contains("profissional");
        // Nada é reservado até o cliente escolher.
        verify(agendaService, never()).adicionarAgendamentoViaIA(any(), any(), any(), any(), any());
        verify(agendaService, never()).adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("escolher o número do barbeiro reserva com o profissional preferido e vai ao pagamento")
    void professionalChoice_number_booksWithPreferred() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        com.razor.api.model.Professional rafael = prof("Rafael");
        com.razor.api.model.Professional bruno = prof("Bruno");
        when(professionalService.listActive()).thenReturn(List.of(rafael, bruno));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE); // pergunta
        service.processCustomerMessage(textWebhook("2"), PHONE);                        // escolhe Bruno

        verify(agendaService).adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte", bruno.getId());
        assertThat(lastClientMessage()).contains("pagamento");
    }

    @Test
    @DisplayName("'tanto faz' reserva com auto-atribuição (preferido nulo)")
    void professionalChoice_tantoFaz_booksAuto() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(professionalService.listActive()).thenReturn(List.of(prof("Rafael"), prof("Bruno")));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        service.processCustomerMessage(textWebhook("tanto faz"), PHONE);

        verify(agendaService).adicionarAgendamentoViaIA(
                eq("João"), eq(PHONE), eq("20/12"), eq("10:00"), eq("Corte"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("resposta da IA sem tag apenas repassa o texto limpo ao cliente")
    void aiPlainResponse_isForwarded() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA()).thenReturn("");
        when(agendaService.getAgendamentosClienteList(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any()))
                .thenReturn("Claro! Temos horários de manhã e à tarde.");

        service.processCustomerMessage(textWebhook("voces atendem a tarde?"), PHONE);

        Interaction saved = captureBotInteraction();
        assertThat(saved.getContent()).isEqualTo("Claro! Temos horários de manhã e à tarde.");
    }

    private Interaction captureBotInteraction() {
        ArgumentCaptor<Interaction> cap = ArgumentCaptor.forClass(Interaction.class);
        verify(interactionRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getAllValues().stream()
                .filter(i -> i.getType() == Interaction.InteractionType.BOT)
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}

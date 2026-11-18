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
    @Mock private PlanPricingService planPricingService;
    @Mock private WaitlistOfferService waitlistOfferService;
    @Mock private AppointmentConfirmationService confirmationService;
    @Mock private NoShowDepositService noShowDepositService;
    @Mock private ProfessionalAssignmentService professionalAssignmentService;

    // Store REAL (nao mock): a maquina de estados da conversa precisa gravar/ler de verdade.
    @org.mockito.Spy
    private com.razor.api.state.StateStore stateStore =
            new com.razor.api.state.InMemoryStateStore(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

    @InjectMocks private CustomerConversationService service;

    /** Config do tenant com a chave PIX preenchida (caso normal de uma barbearia configurada). */
    private void givenPixConfigured(String pixKey) {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(
                        "", "", "", null, "",
                        "Barbearia Teste", pixKey, null, null,
                        "09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT,
                        null, null, null, null, null, null));
    }

    private void givenBotSettings(String address, String mapsUrl, String servicesText, String instagram) {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(address, mapsUrl, servicesText, null, instagram));
    }

    /** Por padrao o agendamento e salvo (o caso feliz); testes de falha sobrescrevem. */
    @org.junit.jupiter.api.BeforeEach
    void bookingSucceedsByDefault() {
        Appointment salvo = Appointment.builder().customerPhone(PHONE).build();
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of(salvo));
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of(salvo));
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
    @DisplayName("'menu' envia o menu textual do cliente (invariante)")
    void menuCommand_sendsMenu() {
        givenExistingCustomer();
        givenBotSettings("", "", "", "");

        service.processCustomerMessage(textWebhook("menu"), PHONE);

        assertThat(captureClientMessage()).isEqualTo(MenuOptions.getMenuAsText("Barbearia Ficticia", 0));
    }

    @Test
    @DisplayName("'opções' também abre o menu (invariante do menu)")
    void opcoesCommand_sendsMenu() {
        givenExistingCustomer();
        givenBotSettings("", "", "", "");

        service.processCustomerMessage(textWebhook("opções"), PHONE);

        assertThat(captureClientMessage()).isEqualTo(MenuOptions.getMenuAsText("Barbearia Ficticia", 0));
    }

    @Test
    @DisplayName("saudação simples abre como ATENDENTE (não o menu numerado) e cita 'menu'")
    void greeting_opensConversationalNotMenu() {
        givenExistingCustomer();
        givenBotSettings("", "", "", ""); // tenant com nome padrão

        service.processCustomerMessage(textWebhook("oi"), PHONE);

        String out = captureClientMessage();
        assertThat(out).isNotEqualTo(MenuOptions.getMenuAsText("Barbearia Ficticia", 0)); // não é o menu seco
        assertThat(out).contains("atendimento").contains("menu");
    }

    @Test
    @DisplayName("'oi, quero marcar um corte' NÃO vira saudação — vai para a IA agendar")
    void greetingWithIntent_goesToAI() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Claro! Pra quando você quer o corte?");

        service.processCustomerMessage(textWebhook("oi, quero marcar um corte"), PHONE);

        // a IA respondeu — não caiu na saudação canned
        assertThat(captureClientMessage()).isEqualTo("Claro! Pra quando você quer o corte?");
        verify(openAIService).processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("serviço citado pelo cliente vai para a agenda e para a IA: horários só de quem faz")
    void serviceMentioned_scopesSlotsToQualified() {
        givenExistingCustomer();
        var barba = new ProfessionalAssignmentService.ServicoPedido("Barba", List.of(UUID.randomUUID()));
        when(professionalAssignmentService.servicoCitado(argThat(falas -> falas != null
                && !falas.isEmpty() && falas.get(0).contains("barba")))).thenReturn(barba);
        when(agendaService.getHorariosOcupadosParaIA(barba)).thenReturn("ocupados da barba");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Tenho 10:00 ou 15:00.");

        service.processCustomerMessage(textWebhook("quero marcar barba amanhã"), PHONE);

        verify(openAIService).processCustomerMessage(any(), any(), eq("ocupados da barba"), anyInt(), any(), any(), eq(barba));
    }

    @Test
    @DisplayName("falas do cliente: atual primeiro, depois as anteriores do cliente (sem as do bot), da mais nova à mais antiga")
    void customerUtterancesOrder() {
        java.time.LocalDateTime t0 = java.time.LocalDateTime.of(2026, 1, 1, 10, 0);
        Interaction antiga = Interaction.builder().type(Interaction.InteractionType.USER).content("quero barba").timestamp(t0).build();
        Interaction bot = Interaction.builder().type(Interaction.InteractionType.BOT).content("Corte?").timestamp(t0.plusMinutes(1)).build();
        Interaction nova = Interaction.builder().type(Interaction.InteractionType.USER).content("amanhã").timestamp(t0.plusMinutes(2)).build();

        assertThat(CustomerConversationService.falasDoCliente("às 15h", List.of(antiga, bot, nova)))
                .containsExactly("às 15h", "amanhã", "quero barba");
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
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());

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
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(ag));

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
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(ag));
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
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");

        // 1a mensagem: a IA devolve a tag de novo agendamento
        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        verify(agendaService).adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        // 2a mensagem: cliente escolhe PIX -> status PIX salvo e a chave DO TENANT enviada
        Appointment ag = Appointment.builder().customerPhone(PHONE).build();
        when(agendaService.getAgendamentoMaisRecente(PHONE)).thenReturn(ag);
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
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");

        // entra em ESPERANDO_PAGAMENTO pelo fluxo real
        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        Appointment ag = Appointment.builder().customerPhone(PHONE).build();
        when(agendaService.getAgendamentoMaisRecente(PHONE)).thenReturn(ag);
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
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");
    }

    @Test
    @DisplayName("com 2+ barbeiros, o agendamento via IA pergunta com qual profissional e ainda NÃO reserva")
    void aiNewAppointment_multipleProfessionals_asksChoice() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(professionalService.listActiveFor(any())).thenReturn(List.of(prof("Rafael"), prof("Bruno")));

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
        when(professionalService.listActiveFor(any())).thenReturn(List.of(rafael, bruno));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE); // pergunta
        service.processCustomerMessage(textWebhook("2"), PHONE);                        // escolhe Bruno

        verify(agendaService).adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte", bruno.getId());
        assertThat(lastClientMessage()).contains("pagamento");
    }

    @Test
    @DisplayName("o bot chama o barbeiro pelo apelido e só oferece quem faz o serviço")
    void professionalChoice_usesNickname_andServiceFilter() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        com.razor.api.model.Professional diego = prof("Diego Aparecido Santos");
        diego.setNickname("Diego");
        when(professionalService.listActiveFor("Corte")).thenReturn(List.of(diego, prof("Bruno")));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        assertThat(captureClientMessage()).contains("Diego").doesNotContain("Aparecido");

        // A agenda confirma que o Diego ficou com o horario: o bot nomeia quem foi ATRIBUIDO.
        var comDiego = com.razor.api.model.Appointment.builder().professionalId(diego.getId()).build();
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), eq(diego.getId())))
                .thenReturn(java.util.Optional.of(comDiego));
        service.processCustomerMessage(textWebhook("1"), PHONE);
        verify(agendaService).adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte", diego.getId());
        assertThat(lastClientMessage()).contains("*Diego*").doesNotContain("Aparecido");
    }

    @Test
    @DisplayName("barbeiro escolhido ocupou no meio-tempo: o bot nomeia quem FICOU com o horario")
    void professionalChoice_namesAssignedBarber_notThePreferred() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        com.razor.api.model.Professional diego = prof("Diego");
        com.razor.api.model.Professional bruno = prof("Bruno");
        when(professionalService.listActiveFor("Corte")).thenReturn(List.of(diego, bruno));
        var comBruno = com.razor.api.model.Appointment.builder().professionalId(bruno.getId()).build();
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), eq(diego.getId())))
                .thenReturn(java.util.Optional.of(comBruno));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        service.processCustomerMessage(textWebhook("1"), PHONE); // pede o Diego

        assertThat(lastClientMessage()).contains("*Bruno*").doesNotContain("*Diego*");
    }

    @Test
    @DisplayName("'tanto faz' reserva com auto-atribuição (preferido nulo)")
    void professionalChoice_tantoFaz_booksAuto() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(professionalService.listActiveFor(any())).thenReturn(List.of(prof("Rafael"), prof("Bruno")));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        service.processCustomerMessage(textWebhook("tanto faz"), PHONE);

        verify(agendaService).adicionarAgendamentoViaIA(
                eq("João"), eq(PHONE), eq("20/12"), eq("10:00"), eq("Corte"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("resposta da IA sem tag apenas repassa o texto limpo ao cliente")
    void aiPlainResponse_isForwarded() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Claro! Temos horários de manhã e à tarde.");

        service.processCustomerMessage(textWebhook("voces atendem a tarde?"), PHONE);

        Interaction saved = captureBotInteraction();
        assertThat(saved.getContent()).isEqualTo("Claro! Temos horários de manhã e à tarde.");
    }

    // ---------- cancelamento pela IA e limite de agendamentos (V36) ----------

    private Appointment futuro(int dia, int hora) {
        return Appointment.builder().customerPhone(PHONE).service("Corte")
                .appointmentDate(LocalDate.of(2026, 12, dia)).appointmentTime(LocalTime.of(hora, 0)).build();
    }

    @Test
    @DisplayName("IA pede cancelamento sem horario futuro: resposta deterministica, nunca o 'cancelado ✅' da IA")
    void aiCancel_nothingToCancel_saysSo() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Prontinho, cancelado ✅ [CANCELAR_AGENDAMENTO]");

        service.processCustomerMessage(textWebhook("cancela meu corte"), PHONE);

        String out = lastClientMessage();
        assertThat(out).contains("Não encontrei").doesNotContain("cancelado ✅");
        verify(agendaService, never()).cancelarAgendamentoExato(any());
        verify(agendaService, never()).cancelarAgendamentoMaisRecente(any());
    }

    @Test
    @DisplayName("IA pede cancelamento com 2 horarios futuros: pergunta qual, nao apaga nenhum")
    void aiCancel_multiple_asksWhich() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(futuro(20, 10), futuro(27, 11)));
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Cancelado ✅ [CANCELAR_AGENDAMENTO]");

        service.processCustomerMessage(textWebhook("quero cancelar"), PHONE);

        assertThat(lastClientMessage()).contains("Qual deles").contains("20/12").contains("27/12");
        verify(agendaService, never()).cancelarAgendamentoExato(any());
    }

    @Test
    @DisplayName("IA pede cancelamento com 1 horario futuro: cancela exatamente esse")
    void aiCancel_single_cancelsThatOne() {
        givenExistingCustomer();
        Appointment ag = futuro(20, 10);
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(ag));
        when(agendaService.cancelarAgendamentoExato(ag)).thenReturn(true);
        when(waitlistRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Cancelado ✅ [CANCELAR_AGENDAMENTO]");

        service.processCustomerMessage(textWebhook("quero cancelar"), PHONE);

        verify(agendaService).cancelarAgendamentoExato(ag);
        assertThat(lastClientMessage()).contains("cancelado com sucesso");
    }

    @Test
    @DisplayName("cliente no limite: a tag de agendamento da IA e recusada com gentileza e lista os horarios")
    void aiNewAppointment_atLimit_refusesKindly() {
        givenExistingCustomer();
        Appointment a = futuro(20, 10);
        Appointment b = futuro(27, 11);
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(a, b));
        when(agendaService.checkBookingLimit(PHONE)).thenReturn(java.util.Optional.of(
                new AgendaBookingService.BookingLimitExceeded(2, List.of(a, b))));
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|30/12|10:00|Corte]");

        service.processCustomerMessage(textWebhook("quero marcar mais um"), PHONE);

        String out = lastClientMessage();
        assertThat(out).contains("2 horários").contains("20/12").contains("27/12").contains("cancelar meu horário");
        assertThat(out).doesNotContain("Agendado!");
        verify(agendaService, never()).adicionarAgendamentoViaIA(any(), any(), any(), any(), any());
        verify(agendaService, never()).adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("contexto da IA conta so horarios futuros (historico nao gera 'limite atingido')")
    void aiContext_countsOnlyFutureAppointments() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of(futuro(20, 10)));
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any())).thenReturn("Oi!");

        service.processCustomerMessage(textWebhook("voces abrem sabado?"), PHONE);

        verify(openAIService).processCustomerMessage(any(), any(), any(), eq(1), any(), any(), any());
    }

    private Interaction captureBotInteraction() {
        ArgumentCaptor<Interaction> cap = ArgumentCaptor.forClass(Interaction.class);
        verify(interactionRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getAllValues().stream()
                .filter(i -> i.getType() == Interaction.InteractionType.BOT)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    // ---------- agendamento que nao foi salvo ----------

    @Test
    @DisplayName("tag da IA que nao gera agendamento NAO confirma nem pede pagamento — pede dia/hora de novo")
    void aiNewAppointment_bookingFails_asksDateAgain() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        String msg = captureClientMessage();
        assertThat(msg).isEqualTo(CustomerConversationService.BOOKING_FAILED_MSG);
        assertThat(msg).doesNotContain("Agendado").doesNotContain("PIX");
        assertThat(stateStore.get(com.razor.api.state.StateKeys.convState(PHONE), String.class)).isEmpty();
    }

    @Test
    @DisplayName("escolha de barbeiro com agendamento que falha nao diz 'reservado'")
    void professionalChoice_bookingFails_asksDateAgain() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        when(professionalService.listActive()).thenReturn(List.of(prof("Rafael"), prof("Bruno")));
        when(agendaService.adicionarAgendamentoViaIA(any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        service.processCustomerMessage(textWebhook("1"), PHONE);

        assertThat(lastClientMessage()).isEqualTo(CustomerConversationService.BOOKING_FAILED_MSG);
        assertThat(stateStore.get(com.razor.api.state.StateKeys.convState(PHONE), String.class)).isEmpty();
    }

    @Test
    @DisplayName("ESPERANDO_PAGAMENTO sem agendamento no banco nao promete '100% garantido'")
    void pixPayment_withoutAppointment_doesNotPromise() {
        givenExistingCustomer();
        givenAiReturnsAgendamentoTag();
        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);

        when(agendaService.getAgendamentoMaisRecente(PHONE)).thenReturn(null);
        givenPixConfigured("pix@barbeariateste.com.br");
        service.processCustomerMessage(textWebhook("1"), PHONE);

        assertThat(lastClientMessage()).isEqualTo(CustomerConversationService.BOOKING_FAILED_MSG);
        verify(messenger, never()).send(eq(PHONE), argThat(m -> m.contains("garantido")), any());
    }

    // ---------- IA indisponivel ----------

    @Test
    @DisplayName("IA fora do ar: contingencia ao cliente sem gravar como fala do bot, e admin avisado uma vez")
    void aiUnavailable_contingencyNotSaved_adminAlertedOnce() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(tenantAdminService.adminPhones()).thenReturn(List.of("5511000000000"));
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new AiUnavailableException("fora", new RuntimeException("429")));

        service.processCustomerMessage(textWebhook("quero marcar um horario"), PHONE);
        service.processCustomerMessage(textWebhook("alguem ai?"), PHONE);

        verify(messenger, org.mockito.Mockito.times(2))
                .send(eq(PHONE), eq(CustomerConversationService.AI_CONTINGENCY_MSG), eq("CLIENTE"));
        // throttle: duas falhas seguidas, um unico aviso
        verify(messenger, org.mockito.Mockito.times(1))
                .send(eq("5511000000000"), argThat(m -> m.contains("IA INDISPONÍVEL")), eq("ADMIN"));
        // so as mensagens do cliente foram gravadas; nenhuma fala de BOT
        verify(interactionRepository, never()).save(argThat(i -> i.getType() == Interaction.InteractionType.BOT));
    }

    // ---------- anti-falta (V42) ----------

    @Test
    @DisplayName("resposta '1' a confirmacao da vespera e tratada sem a IA e sem abrir o agendamento do menu")
    void confirmationReply_handledWithoutAi() {
        givenExistingCustomer();
        when(confirmationService.handleReply(PHONE, "1")).thenReturn(java.util.Optional.of("Show! ✅ Horário confirmado"));

        service.processCustomerMessage(textWebhook("1"), PHONE);

        assertThat(captureClientMessage()).contains("confirmado");
        verify(openAIService, never()).processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("no meio da escolha de pagamento, '1' e PIX — nao vai para a confirmacao")
    void paymentState_winsOverConfirmation() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");
        service.processCustomerMessage(textWebhook("quero marcar"), PHONE);
        when(agendaService.getAgendamentoMaisRecente(PHONE)).thenReturn(Appointment.builder().customerPhone(PHONE).build());
        givenPixConfigured("pix@x.com");

        service.processCustomerMessage(textWebhook("1"), PHONE);

        verify(confirmationService, never()).handleReply(eq(PHONE), eq("1"));
        verify(appointmentRepository).save(argThat(a -> a.getPaymentStatus() == PaymentStatus.PIX));
    }

    @Test
    @DisplayName("cliente que falta muito: no lugar da pergunta de pagamento vai o PIX do sinal")
    void depositRequired_replacesPaymentQuestion() {
        givenExistingCustomer();
        when(agendaService.getHorariosOcupadosParaIA(any())).thenReturn("");
        when(agendaService.getAgendamentosFuturos(PHONE)).thenReturn(List.of());
        when(openAIService.processCustomerMessage(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn("Agendado! [NOVO_AGENDAMENTO|João|20/12|10:00|Corte]");
        when(noShowDepositService.requireDepositIfNeeded(any(), any()))
                .thenReturn(java.util.Optional.of("pré-reservado ... sinal ... CHAVE PIX"));

        service.processCustomerMessage(textWebhook("quero marcar"), PHONE);
        assertThat(lastClientMessage()).contains("sinal");

        // Sem estado de pagamento: o "1" seguinte nao vira escolha de PIX.
        service.processCustomerMessage(textWebhook("1"), PHONE);
        verify(agendaService, never()).getAgendamentoMaisRecente(PHONE);
    }

    @Test
    @DisplayName("vaga da lista de espera aceita conta como vaga reaproveitada")
    void waitlistAccept_recordsRefill() {
        givenExistingCustomer();
        stateStore.put(com.razor.api.state.StateKeys.waitlistOffer(PHONE),
                new WaitlistOfferService.WaitlistOffer(PHONE, "João", "20/12", "10:00", "Corte"), java.time.Duration.ofHours(1));

        service.processCustomerMessage(textWebhook("sim"), PHONE);

        verify(confirmationService).record(eq(com.razor.api.model.AiImpactEvent.Type.WAITLIST_REFILL), any());
    }
}

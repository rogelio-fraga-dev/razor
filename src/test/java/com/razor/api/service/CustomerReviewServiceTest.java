package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Comanda;
import com.razor.api.model.CustomerReview;
import com.razor.api.repository.CustomerReviewRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.state.InMemoryStateStore;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.WorkingDays;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Regras do pedido de avaliacao pos-atendimento (V46). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerReviewServiceTest {

    private static final String PHONE = "5511988887777";
    private static final UUID SHOP = UUID.randomUUID();
    private static final String GOOGLE = "https://g.page/r/barbearia-teste/review";

    @Mock private CustomerReviewRepository repository;
    @Mock private BarbershopSettingsService settingsService;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private CustomerService customerService;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private WhatsAppMessenger messenger;

    private final StateStore stateStore =
            new InMemoryStateStore(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    private CustomerReviewService service;

    // Quarta-feira 18/11/2026, 14:00.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 14, 0);

    @BeforeEach
    void setUp() {
        TenantContext.set(SHOP);
        service = new CustomerReviewService(repository, settingsService, tenantAdminService, customerService,
                professionalRepository, stateStore, messenger);
        when(settingsService.getReviewSettings())
                .thenReturn(new BarbershopSettingsService.ReviewSettings(true, GOOGLE, 120));
        when(settingsService.resolve()).thenReturn(new BarbershopSettingsService.BotSettings(
                "", "", "", null, "", "Barbearia Teste", null, null, null,
                "09:00", "19:00", WorkingDays.DEFAULT, null, null, null, null, null, null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tenantAdminService.adminPhones()).thenReturn(List.of("5511900000001"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Comanda comanda() {
        return Comanda.builder().id(UUID.randomUUID()).barbershopId(SHOP).customerName("Carlos Souza")
                .customerPhone(PHONE).build();
    }

    // ---------- programar ----------

    @Test
    @DisplayName("comanda fechada: pedido programado para 2h depois, dentro do expediente")
    void schedule_afterDelay() {
        Optional<CustomerReview> r = service.doSchedule(comanda(), PHONE, NOW);
        assertThat(r).isPresent();
        assertThat(r.get().getScheduledFor()).isEqualTo(NOW.plusHours(2));
        assertThat(r.get().getStatus()).isEqualTo(CustomerReview.Status.SCHEDULED);
    }

    @Test
    @DisplayName("fechou tarde: o pedido vai para a abertura do próximo dia aberto")
    void schedule_afterHours_movesToNextOpening() {
        LocalDateTime tarde = LocalDateTime.of(2026, 11, 21, 18, 0); // sabado 18h + 2h = 20h, domingo fechado
        Optional<CustomerReview> r = service.doSchedule(comanda(), PHONE, tarde);
        assertThat(r.get().getScheduledFor()).isEqualTo(LocalDateTime.of(2026, 11, 23, 9, 0)); // segunda 09:00
    }

    @Test
    @DisplayName("horário comercial: antes de abrir vai para a abertura; dia fechado pula")
    void nextSendTime_rules() {
        var dias = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
        LocalTime abre = LocalTime.of(9, 0);
        LocalTime fecha = LocalTime.of(19, 0);
        LocalDate quarta = LocalDate.of(2026, 11, 18);
        assertThat(CustomerReviewService.nextSendTime(quarta.atTime(7, 30), dias, abre, fecha)).isEqualTo(quarta.atTime(9, 0));
        assertThat(CustomerReviewService.nextSendTime(quarta.atTime(12, 15), dias, abre, fecha)).isEqualTo(quarta.atTime(12, 15));
        assertThat(CustomerReviewService.nextSendTime(quarta.atTime(19, 0), dias, abre, fecha))
                .isEqualTo(LocalDate.of(2026, 11, 23).atTime(9, 0)); // qui-dom fechados
    }

    @Test
    @DisplayName("no máximo um pedido a cada 30 dias por cliente")
    void schedule_frequencyLimit() {
        when(repository.existsByBarbershopIdAndCustomerPhoneAndStatusInAndCreatedAtAfter(
                eq(SHOP), eq(PHONE), any(), eq(NOW.minusDays(30)))).thenReturn(true);
        assertThat(service.doSchedule(comanda(), PHONE, NOW)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("quem pediu para parar, admin, avaliação desligada ou comanda repetida: nada programado")
    void schedule_blockedCases() {
        when(repository.existsByBarbershopIdAndCustomerPhoneAndStatus(SHOP, PHONE, CustomerReview.Status.OPTED_OUT))
                .thenReturn(true);
        assertThat(service.doSchedule(comanda(), PHONE, NOW)).isEmpty();

        when(repository.existsByBarbershopIdAndCustomerPhoneAndStatus(SHOP, PHONE, CustomerReview.Status.OPTED_OUT))
                .thenReturn(false);
        when(tenantAdminService.isAdmin(PHONE)).thenReturn(true);
        assertThat(service.doSchedule(comanda(), PHONE, NOW)).isEmpty();

        when(tenantAdminService.isAdmin(PHONE)).thenReturn(false);
        Comanda c = comanda();
        when(repository.existsByComandaId(c.getId())).thenReturn(true);
        assertThat(service.doSchedule(c, PHONE, NOW)).isEmpty();

        when(settingsService.getReviewSettings())
                .thenReturn(new BarbershopSettingsService.ReviewSettings(false, GOOGLE, 120));
        assertThat(service.doSchedule(comanda(), PHONE, NOW)).isEmpty();
        verify(repository, never()).save(any());
    }

    // ---------- enviar ----------

    private CustomerReview scheduled() {
        return CustomerReview.builder().id(UUID.randomUUID()).barbershopId(SHOP).customerPhone(PHONE)
                .customerName("Carlos Souza").status(CustomerReview.Status.SCHEDULED).scheduledFor(NOW).build();
    }

    @Test
    @DisplayName("envio: pergunta de 1 a 5 sai e a conversa fica esperando a nota")
    void send_armsConversation() {
        CustomerReview r = scheduled();
        assertThat(service.sendOne(r, NOW)).isTrue();
        verify(messenger).sendSync(eq(PHONE), argThat(m -> m.contains("1 a 5") && m.contains("parar")), eq("SISTEMA"));
        assertThat(r.getStatus()).isEqualTo(CustomerReview.Status.SENT);
        assertThat(stateStore.get(StateKeys.review(PHONE), CustomerReviewService.PendingReview.class)).isPresent();
    }

    @Test
    @DisplayName("cliente em atendimento humano ou com a confirmação da véspera aberta: adia, não envia")
    void send_postponed() {
        when(customerService.isCustomerPaused(PHONE)).thenReturn(true);
        CustomerReview r = scheduled();
        assertThat(service.sendOne(r, NOW)).isFalse();
        assertThat(r.getScheduledFor()).isEqualTo(NOW.plusHours(1));
        assertThat(r.getStatus()).isEqualTo(CustomerReview.Status.SCHEDULED);

        when(customerService.isCustomerPaused(PHONE)).thenReturn(false);
        stateStore.put(StateKeys.confirmation(PHONE), new AppointmentConfirmationService.PendingConfirmation(1L),
                java.time.Duration.ofHours(5));
        assertThat(service.sendOne(scheduled(), NOW)).isFalse();
        verify(messenger, never()).sendSync(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("motor atrasado depois do expediente: reprograma para a abertura, não envia de noite")
    void send_outsideHours_rescheduled() {
        CustomerReview r = scheduled();
        LocalDateTime noite = NOW.withHour(22);
        assertThat(service.sendOne(r, noite)).isFalse();
        assertThat(r.getScheduledFor()).isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 0));
        verify(messenger, never()).sendSync(anyString(), anyString(), anyString());
    }

    // ---------- resposta ----------

    private CustomerReview armed() {
        CustomerReview r = scheduled();
        service.sendOne(r, NOW);
        when(repository.findByIdAndBarbershopId(r.getId(), SHOP)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("nota 5: agradece e manda o link do Google")
    void reply_high_sendsGoogleLink() {
        CustomerReview r = armed();
        Optional<String> resp = service.handleReply(PHONE, "5", false);
        assertThat(resp.get()).contains(GOOGLE);
        assertThat(r.getRating()).isEqualTo(5);
        assertThat(r.getStatus()).isEqualTo(CustomerReview.Status.RATED);
        assertThat(stateStore.get(StateKeys.review(PHONE), CustomerReviewService.PendingReview.class)).isEmpty();
        verify(messenger, never()).send(anyString(), anyString(), eq("ADMIN"));
    }

    @Test
    @DisplayName("nota 4 sem link configurado: só agradece")
    void reply_high_noLink() {
        when(settingsService.getReviewSettings()).thenReturn(new BarbershopSettingsService.ReviewSettings(true, null, 120));
        armed();
        assertThat(service.handleReply(PHONE, "nota 4", false).get()).doesNotContain("http");
    }

    @Test
    @DisplayName("nota 2: nunca vai para o Google — pergunta o que houve e avisa o dono; o comentário também é repassado")
    void reply_low_alertsOwner() {
        CustomerReview r = armed();
        String resp = service.handleReply(PHONE, "2", false).get();
        assertThat(resp).doesNotContain("google").doesNotContain("g.page").contains("O que não foi legal");
        verify(messenger).send(eq("5511900000001"), argThat(m -> m.contains("Avaliação baixa") && m.contains("*2*")), eq("ADMIN"));

        String resp2 = service.handleReply(PHONE, "demorou muito pra me atender", false).get();
        assertThat(resp2).contains("responsável");
        assertThat(r.getComment()).isEqualTo("demorou muito pra me atender");
        verify(messenger).send(eq("5511900000001"), argThat(m -> m.contains("demorou muito")), eq("ADMIN"));
        assertThat(stateStore.get(StateKeys.review(PHONE), CustomerReviewService.PendingReview.class)).isEmpty();
    }

    @Test
    @DisplayName("'parar' durante o pedido: opt-out gravado, nunca mais é perguntado")
    void reply_optOut() {
        CustomerReview r = armed();
        assertThat(service.handleReply(PHONE, "Parar", false).get()).contains("Não vou mais");
        assertThat(r.getStatus()).isEqualTo(CustomerReview.Status.OPTED_OUT);
    }

    @Test
    @DisplayName("resposta que não é nota segue o fluxo normal e o pedido continua aberto")
    void reply_notARating_passesThrough() {
        armed();
        assertThat(service.handleReply(PHONE, "quero marcar um corte amanhã", false)).isEmpty();
        assertThat(stateStore.get(StateKeys.review(PHONE), CustomerReviewService.PendingReview.class)).isPresent();
    }

    @Test
    @DisplayName("'1'/'2' puros com a confirmação da véspera aberta ficam com a confirmação")
    void reply_ambiguousWithConfirmation() {
        armed();
        assertThat(service.handleReply(PHONE, "1", true)).isEmpty();
        assertThat(service.handleReply(PHONE, "5", true)).isPresent();
    }

    @Test
    @DisplayName("sem pedido aberto, '5' não é nota (vai para o menu)")
    void reply_withoutPending() {
        assertThat(service.handleReply(PHONE, "5", false)).isEmpty();
    }

    @Test
    @DisplayName("leitura da nota: número, palavra (áudio), estrelas, 4/5 e 10")
    void parseRating() {
        assertThat(CustomerReviewService.parseRating("5")).isEqualTo(5);
        assertThat(CustomerReviewService.parseRating("Nota cinco.")).isEqualTo(5);
        assertThat(CustomerReviewService.parseRating("4 estrelas")).isEqualTo(4);
        assertThat(CustomerReviewService.parseRating("4/5")).isEqualTo(4);
        assertThat(CustomerReviewService.parseRating("⭐⭐⭐")).isEqualTo(3);
        assertThat(CustomerReviewService.parseRating("10")).isEqualTo(5);
        assertThat(CustomerReviewService.parseRating("dou 3")).isEqualTo(3);
        assertThat(CustomerReviewService.parseRating("7")).isNull();
        assertThat(CustomerReviewService.parseRating("0")).isNull();
        assertThat(CustomerReviewService.parseRating("quero 2 cortes")).isNull();
        assertThat(CustomerReviewService.parseRating("amanhã às 5")).isNull();
    }

    @Test
    @DisplayName("resumo do painel: média, distribuição e taxa de resposta")
    void summary() {
        when(repository.ratingStats(eq(SHOP), any())).thenReturn(List.<Object[]>of(new Object[]{4.333, 3L}));
        when(repository.ratingDistribution(eq(SHOP), any()))
                .thenReturn(List.of(new Object[]{5, 2L}, new Object[]{3, 1L}));
        when(repository.countByBarbershopIdAndSentAtAfter(eq(SHOP), any())).thenReturn(6L);
        when(repository.findByBarbershopIdAndRatingIsNotNullOrderByRatedAtDesc(eq(SHOP), any())).thenReturn(List.of());

        var s = service.summary(90);

        assertThat(s.average()).isEqualTo(4.3);
        assertThat(s.count()).isEqualTo(3);
        assertThat(s.distribution()).containsEntry(5, 2L).containsEntry(4, 0L).containsEntry(3, 1L);
        assertThat(s.responseRate()).isEqualTo(0.5);
        assertThat(s.googleLinkConfigured()).isTrue();
    }

    @Test
    @DisplayName("link do Google: só https em domínio do Google, g.page ou maps.app.goo.gl")
    void googleUrlPolicy() {
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://g.page/r/abc/review")).isTrue();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://search.google.com/local/writereview?placeid=x")).isTrue();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://www.google.com.br/maps/place/x")).isTrue();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://maps.app.goo.gl/xyz")).isTrue();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("http://g.page/r/abc")).isFalse();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://google.com.evil.io/x")).isFalse();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://evilgoogle.com/x")).isFalse();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("https://user@g.page/x")).isFalse();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.isValid("javascript:alert(1)")).isFalse();
        assertThat(com.razor.api.util.GoogleReviewUrlPolicy.normalize("  ")).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                com.razor.api.util.GoogleReviewUrlPolicy.normalize("https://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

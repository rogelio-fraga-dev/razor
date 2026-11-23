package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.AutomationSend;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.Coupon;
import com.razor.api.model.Customer;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.AutomationSendRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationCampaignServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23); // segunda
    private static final LocalTime NOW = LocalTime.of(10, 0);
    private static final String PHONE = "5511988887777";

    private AutomationSettings cfg;
    private final Map<String, Integer> returnDays = new HashMap<>();
    private final Set<String> settled = new java.util.HashSet<>();
    private final Set<String> capped = new java.util.HashSet<>();
    private final Set<String> excluded = new java.util.HashSet<>();

    @BeforeEach
    void setUp() {
        cfg = AutomationSettings.defaults(UUID.randomUUID());
    }

    private static Customer customer(String phone) {
        return Customer.builder().id(UUID.randomUUID()).phoneNumber(phone).name("João Silva").build();
    }

    private static Appointment visit(String phone, LocalDate date, String service) {
        return Appointment.builder().id((long) Math.abs(date.hashCode())).customerPhone(phone)
                .appointmentDate(date).appointmentTime(LocalTime.of(10, 0)).service(service).build();
    }

    private List<AutomationCampaignService.Candidate> plan(List<Customer> cs, List<Appointment> appts) {
        return AutomationCampaignService.plan(new AutomationCampaignService.PlanInput(cfg, cs, appts, returnDays,
                TODAY, NOW, settled, capped, excluded));
    }

    // ------------------------------------------------------------ sumiu

    @Test
    @DisplayName("sumiu: cliente com a ultima visita ha mais que o limite recebe, com os dias no texto")
    void lapsed_afterThreshold() {
        cfg.setLapsedEnabled(true);
        var out = plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(35), "Corte")));
        assertThat(out).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(AutomationSend.Kind.LAPSED);
            assertThat(c.vars()).containsEntry("dias", "35").containsEntry("nome", "João");
            assertThat(c.referenceKey()).isEqualTo(TODAY.minusDays(35).toString());
        });
    }

    @Test
    @DisplayName("sumiu: antes do limite, com horario futuro, nunca veio ou ha muito tempo — nada")
    void lapsed_notEligible() {
        cfg.setLapsedEnabled(true);
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(20), "Corte")))).isEmpty();
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(40), "Corte"),
                visit(PHONE, TODAY.plusDays(2), "Corte")))).isEmpty();
        assertThat(plan(List.of(customer(PHONE)), List.of())).isEmpty();
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(300), "Corte")))).isEmpty();
    }

    @Test
    @DisplayName("sumiu: falta nao conta como visita; telefone em outro formato casa")
    void lapsed_ignoresNoShow_andMatchesPhoneFormats() {
        cfg.setLapsedEnabled(true);
        Appointment falta = visit("11988887777", TODAY.minusDays(5), "Corte");
        falta.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        var out = plan(List.of(customer(PHONE)), List.of(visit("11988887777", TODAY.minusDays(40), "Corte"), falta));
        assertThat(out).singleElement().satisfies(c -> assertThat(c.vars()).containsEntry("dias", "40"));
    }

    @Test
    @DisplayName("ocasiao ja resolvida, opt-out, limite de frequencia, admin/pausado e telefone invalido nao recebem")
    void filters() {
        cfg.setLapsedEnabled(true);
        List<Appointment> appts = List.of(visit(PHONE, TODAY.minusDays(35), "Corte"));

        settled.add(AutomationCampaignService.key(AutomationSend.Kind.LAPSED, PHONE, TODAY.minusDays(35).toString()));
        assertThat(plan(List.of(customer(PHONE)), appts)).isEmpty();
        settled.clear();

        Customer optOut = customer(PHONE);
        optOut.setMarketingOptOutAt(LocalDateTime.now());
        assertThat(plan(List.of(optOut), appts)).isEmpty();

        capped.add(PHONE);
        assertThat(plan(List.of(customer(PHONE)), appts)).isEmpty();
        capped.clear();

        excluded.add(PHONE);
        assertThat(plan(List.of(customer(PHONE)), appts)).isEmpty();
        excluded.clear();

        assertThat(plan(List.of(customer("123")), List.of(visit("123", TODAY.minusDays(35), "Corte")))).isEmpty();
    }

    // ------------------------------------------------------------ aniversario

    @Test
    @DisplayName("aniversario: no dia e ate 3 dias depois; antes nao; uma vez por ano")
    void birthday_window() {
        cfg.setBirthdayEnabled(true);
        Customer c = customer(PHONE);
        c.setBirthDate(LocalDate.of(2000, 11, 21));
        assertThat(plan(List.of(c), List.of())).singleElement()
                .satisfies(cand -> assertThat(cand.referenceKey()).isEqualTo("2026"));

        c.setBirthDate(LocalDate.of(1990, 11, 19)); // 4 dias atras
        assertThat(plan(List.of(c), List.of())).isEmpty();
        c.setBirthDate(LocalDate.of(1990, 11, 24)); // amanha
        assertThat(plan(List.of(c), List.of())).isEmpty();

        c.setBirthDate(LocalDate.of(1990, 11, 23));
        settled.add(AutomationCampaignService.key(AutomationSend.Kind.BIRTHDAY, PHONE, "2026"));
        assertThat(plan(List.of(c), List.of())).isEmpty();
    }

    @Test
    @DisplayName("aniversario tem prioridade sobre o sumiu e vem primeiro na fila")
    void birthday_firstAndPreferred() {
        cfg.setBirthdayEnabled(true);
        cfg.setLapsedEnabled(true);
        Customer niver = customer(PHONE);
        niver.setBirthDate(LocalDate.of(1990, 11, 23));
        Customer outro = customer("5511977776666");
        var out = plan(List.of(outro, niver), List.of(visit(PHONE, TODAY.minusDays(40), "Corte"),
                visit("5511977776666", TODAY.minusDays(40), "Corte")));
        assertThat(out).extracting(AutomationCampaignService.Candidate::kind)
                .containsExactly(AutomationSend.Kind.BIRTHDAY, AutomationSend.Kind.LAPSED);
    }

    // ------------------------------------------------------------ retorno

    @Test
    @DisplayName("retorno: N dias depois do servico configurado (acento/caixa nao importam), ate 7 de tolerancia")
    void returnReminder() {
        cfg.setReturnEnabled(true);
        returnDays.put(AutomationCampaignService.normalize("Barba"), 15);
        var out = plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(16), "BARBA")));
        assertThat(out).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(AutomationSend.Kind.RETURN);
            assertThat(c.vars()).containsEntry("servico", "barba").containsEntry("dias", "16");
        });
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(10), "Barba")))).isEmpty();
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(30), "Barba")))).isEmpty();
        assertThat(plan(List.of(customer(PHONE)), List.of(visit(PHONE, TODAY.minusDays(16), "Corte")))).isEmpty();
    }

    @Test
    @DisplayName("teto de mensagens por rodada")
    void capPerRun() {
        cfg.setLapsedEnabled(true);
        List<Customer> cs = new ArrayList<>();
        List<Appointment> as = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String p = "55119" + String.format("%08d", 10_000_000 + i);
            cs.add(customer(p));
            as.add(visit(p, TODAY.minusDays(35), "Corte"));
        }
        assertThat(plan(cs, as)).hasSize(AutomationCampaignService.MAX_PER_RUN);
    }

    @Test
    @DisplayName("horario comercial: dia aberto e entre abertura (inclusive) e fechamento (exclusive)")
    void businessHours() {
        Set<DayOfWeek> dias = EnumSet.of(DayOfWeek.MONDAY);
        LocalTime abre = LocalTime.of(9, 0), fecha = LocalTime.of(19, 0);
        assertThat(AutomationCampaignService.withinBusinessHours(TODAY.atTime(9, 0), dias, abre, fecha)).isTrue();
        assertThat(AutomationCampaignService.withinBusinessHours(TODAY.atTime(8, 59), dias, abre, fecha)).isFalse();
        assertThat(AutomationCampaignService.withinBusinessHours(TODAY.atTime(19, 0), dias, abre, fecha)).isFalse();
        assertThat(AutomationCampaignService.withinBusinessHours(TODAY.plusDays(1).atTime(10, 0), dias, abre, fecha)).isFalse();
    }

    // ------------------------------------------------------------ envio

    @org.junit.jupiter.api.Nested
    class Deliver {
        private final AutomationSendRepository sendRepository = mock(AutomationSendRepository.class);
        private final BarbershopSettingsService bot = mock(BarbershopSettingsService.class);
        private final CouponService couponService = mock(CouponService.class);
        private final WhatsAppMessenger messenger = mock(WhatsAppMessenger.class);
        private final UUID shop = UUID.randomUUID();
        private AutomationCampaignService service;

        @BeforeEach
        void setUp() {
            TenantContext.set(shop);
            service = new AutomationCampaignService(mock(AutomationSettingsService.class), sendRepository,
                    mock(CustomerRepository.class), mock(AppointmentRepository.class),
                    mock(ServiceOfferingRepository.class), mock(TenantAdminService.class), mock(CustomerService.class),
                    bot, couponService, messenger, 0);
            when(bot.resolve()).thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
            when(sendRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(sendRepository.findByBarbershopIdAndKindAndCustomerPhoneAndReferenceKey(any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
        }

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        private AutomationCampaignService.Candidate cand(AutomationSend.Kind kind) {
            Map<String, String> vars = new HashMap<>();
            vars.put("nome", "João");
            vars.put("dias", "35");
            return new AutomationCampaignService.Candidate(kind, customer(PHONE), "2026", vars);
        }

        @Test
        @DisplayName("envia, marca SENT e o texto leva o rodape de opt-out")
        void sendsAndMarks() {
            int n = service.deliver(AutomationSettings.defaults(shop), List.of(cand(AutomationSend.Kind.LAPSED)),
                    LocalDateTime.of(TODAY, NOW));
            assertThat(n).isEqualTo(1);
            var texto = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(messenger).sendSync(eq(PHONE), texto.capture(), eq("AUTOMACAO"));
            assertThat(texto.getValue()).contains("Faz 35 dias").contains("é só responder aqui")
                    .endsWith(AutomationTemplates.OPT_OUT_FOOTER);
            var row = org.mockito.ArgumentCaptor.forClass(AutomationSend.class);
            verify(sendRepository, times(2)).save(row.capture());
            assertThat(row.getValue().getStatus()).isEqualTo(AutomationSend.Status.SENT);
        }

        @Test
        @DisplayName("ocasiao ja enviada nao sai de novo")
        void settledSkipped() {
            AutomationSend ja = AutomationSend.builder().status(AutomationSend.Status.SENT).attempts(1).build();
            when(sendRepository.findByBarbershopIdAndKindAndCustomerPhoneAndReferenceKey(any(), any(), any(), any()))
                    .thenReturn(Optional.of(ja));
            assertThat(service.deliver(AutomationSettings.defaults(shop), List.of(cand(AutomationSend.Kind.LAPSED)),
                    LocalDateTime.of(TODAY, NOW))).isZero();
            verify(messenger, never()).sendSync(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("falha no envio vira FAILED com o erro, e o resto do plano continua")
        void failureRecorded() {
            org.mockito.Mockito.doThrow(new RuntimeException("evolution fora")).doNothing()
                    .when(messenger).sendSync(anyString(), anyString(), anyString());
            int n = service.deliver(AutomationSettings.defaults(shop),
                    List.of(cand(AutomationSend.Kind.LAPSED), cand(AutomationSend.Kind.RETURN)), LocalDateTime.of(TODAY, NOW));
            assertThat(n).isEqualTo(1);
            var rows = org.mockito.ArgumentCaptor.forClass(AutomationSend.class);
            verify(sendRepository, org.mockito.Mockito.atLeast(2)).save(rows.capture());
            assertThat(rows.getAllValues()).anySatisfy(r -> {
                assertThat(r.getStatus()).isEqualTo(AutomationSend.Status.FAILED);
                assertThat(r.getLastError()).isEqualTo("evolution fora");
            });
        }

        @Test
        @DisplayName("aniversario emite o cupom uma vez e o texto traz codigo, desconto e validade")
        void birthdayCoupon() {
            Coupon cupom = mock(Coupon.class);
            when(cupom.getCode()).thenReturn("NIVER-XY12Z");
            when(couponService.issueBirthdayCoupon(eq(PHONE), any(), any(Integer.class))).thenReturn(cupom);
            AutomationSettings c = AutomationSettings.defaults(shop);
            c.setBirthdayDiscountPercent(new BigDecimal("15.00"));
            service.deliver(c, List.of(cand(AutomationSend.Kind.BIRTHDAY)), LocalDateTime.of(TODAY, NOW));
            var texto = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(messenger).sendSync(eq(PHONE), texto.capture(), eq("AUTOMACAO"));
            assertThat(texto.getValue()).contains("NIVER-XY12Z").contains("15% de desconto");
            verify(couponService, times(1)).issueBirthdayCoupon(any(), any(), any(Integer.class));
        }
    }
}

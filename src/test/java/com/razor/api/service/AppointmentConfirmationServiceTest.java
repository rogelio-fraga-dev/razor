package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.AiImpactEvent;
import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.ConfirmationStatus;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.AiImpactEventRepository;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.state.InMemoryStateStore;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Confirmacao da vespera (V42): pergunta, respostas 1/2 e prazo sem resposta. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentConfirmationServiceTest {

    private static final String PHONE = "5511988887777";
    private static final String ADMIN = "5511900000000";

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AgendaBookingService bookingService;
    @Mock private WaitlistOfferService waitlistOfferService;
    @Mock private WhatsAppMessenger messenger;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private AiImpactEventRepository eventRepository;

    private final StateStore stateStore = new InMemoryStateStore(new ObjectMapper().findAndRegisterModules());
    private AppointmentConfirmationService service;
    private Appointment ag;

    @BeforeEach
    void setUp() {
        service = new AppointmentConfirmationService(appointmentRepository, bookingService, waitlistOfferService,
                stateStore, messenger, tenantAdminService, barbershopSettingsService, eventRepository);
        ag = Appointment.builder().id(42L).barbershopId(TenantContext.getCurrent())
                .customerPhone(PHONE).customerName("Fulano de Tal").service("Corte")
                .appointmentDate(LocalDate.now().plusDays(1)).appointmentTime(LocalTime.of(15, 0)).build();
        when(appointmentRepository.findByIdAndBarbershopId(42L, TenantContext.getCurrent())).thenReturn(Optional.of(ag));
        when(tenantAdminService.adminPhones()).thenReturn(List.of(ADMIN));
        when(barbershopSettingsService.getNoShowSettings()).thenReturn(BarbershopSettingsService.NoShowSettings.DEFAULTS);
    }

    private ScheduledTask task(String type) {
        return ScheduledTask.builder().barbershopId(TenantContext.getCurrent()).customerPhone(PHONE)
                .appointmentId(42L).taskType(type).messageContent("Responde 1 ou 2").build();
    }

    private void pergunta() {
        service.sendRequest(task(AgendaTaskService.CONFIRMATION_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "Sim", "sim!", "confirmo", "Confirmado", "ok", " 1 "})
    void parse_confirm(String msg) {
        assertThat(AppointmentConfirmationService.parseAnswer(msg)).isEqualTo(AppointmentConfirmationService.Answer.CONFIRM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "não", "nao", "desmarcar", "Não vou poder", "cancelar"})
    void parse_decline(String msg) {
        assertThat(AppointmentConfirmationService.parseAnswer(msg)).isEqualTo(AppointmentConfirmationService.Answer.DECLINE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sim, mas posso ir as 16h?", "3", "qual o endereço?", ""})
    void parse_other(String msg) {
        assertThat(AppointmentConfirmationService.parseAnswer(msg)).isNull();
    }

    @Test
    @DisplayName("pergunta enviada: agendamento fica PENDING e a conversa ganha a chave do agendamento")
    void sendRequest_marksPendingAndArmsState() {
        pergunta();

        verify(messenger).sendSync(PHONE, "Responde 1 ou 2", "SISTEMA");
        assertThat(ag.getConfirmationStatus()).isEqualTo(ConfirmationStatus.PENDING);
        assertThat(stateStore.get(StateKeys.confirmation(PHONE), AppointmentConfirmationService.PendingConfirmation.class))
                .get().extracting(AppointmentConfirmationService.PendingConfirmation::getAppointmentId).isEqualTo(42L);
    }

    @Test
    @DisplayName("agendamento que sumiu ou ja teve comparecimento nao e perguntado")
    void sendRequest_skipsGoneOrResolved() {
        ag.setAttendanceStatus(AttendanceStatus.ATTENDED);
        assertThat(service.sendRequest(task(AgendaTaskService.CONFIRMATION_REQUEST))).isFalse();
        verify(messenger, never()).sendSync(any(), any(), any());
    }

    @Test
    @DisplayName("'1' confirma: CONFIRMED, evento registrado, chave removida")
    void reply1_confirms() {
        pergunta();

        Optional<String> resp = service.handleReply(PHONE, "1");

        assertThat(resp).get().asString().contains("confirmado");
        assertThat(ag.getConfirmationStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        verify(eventRepository).save(argThat(e -> e.getEventType() == AiImpactEvent.Type.CONFIRMED && e.getAppointmentId() == 42L));
        verify(bookingService, never()).cancelarAgendamentoExato(any());
        assertThat(stateStore.get(StateKeys.confirmation(PHONE), Object.class)).isEmpty();
    }

    @Test
    @DisplayName("'2' desmarca SO este horario pelo caminho do bot e oferece a vaga a lista de espera")
    void reply2_cancelsAndOffersWaitlist() {
        pergunta();

        Optional<String> resp = service.handleReply(PHONE, "2");

        assertThat(resp).get().asString().contains("Desmarquei");
        verify(bookingService).cancelarAgendamentoExato(ag);
        verify(waitlistOfferService).offerFreedSlot(ag);
        verify(eventRepository).save(argThat(e -> e.getEventType() == AiImpactEvent.Type.DECLINED));
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Fulano de Tal")), eq("ADMIN"));
    }

    @Test
    @DisplayName("mensagem que nao e resposta segue o fluxo normal e a pergunta continua valendo")
    void otherMessage_passesThrough() {
        pergunta();

        assertThat(service.handleReply(PHONE, "qual o endereço?")).isEmpty();
        assertThat(stateStore.get(StateKeys.confirmation(PHONE), AppointmentConfirmationService.PendingConfirmation.class)).isPresent();
        assertThat(service.handleReply(PHONE, "sim")).isPresent();
    }

    @Test
    @DisplayName("sem pergunta pendente, '1' nao e capturado (vai para o menu)")
    void noPending_notCaptured() {
        assertThat(service.handleReply(PHONE, "1")).isEmpty();
    }

    @Test
    @DisplayName("sem resposta no prazo: NO_RESPONSE e aviso ao admin, SEM cancelar (padrao)")
    void deadline_noResponse_notifiesOnly() {
        pergunta();

        service.handleDeadline(task(AgendaTaskService.CONFIRMATION_DEADLINE));

        assertThat(ag.getConfirmationStatus()).isEqualTo(ConfirmationStatus.NO_RESPONSE);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Fulano de Tal") && m.contains("15:00")
                && m.contains("não confirmou")), eq("ADMIN"));
        verify(bookingService, never()).cancelarAgendamentoExato(any());
        verify(waitlistOfferService, never()).offerFreedSlot(any());
    }

    @Test
    @DisplayName("sem resposta com liberacao automatica ligada: cancela e oferece a vaga")
    void deadline_autoRelease() {
        when(barbershopSettingsService.getNoShowSettings())
                .thenReturn(new BarbershopSettingsService.NoShowSettings(true, 18, 3, true, null, null));
        pergunta();

        service.handleDeadline(task(AgendaTaskService.CONFIRMATION_DEADLINE));

        verify(bookingService).cancelarAgendamentoExato(ag);
        verify(waitlistOfferService).offerFreedSlot(ag);
        verify(messenger).send(eq(PHONE), anyString(), eq("SISTEMA"));
        verify(eventRepository).save(argThat(e -> e.getEventType() == AiImpactEvent.Type.AUTO_RELEASED));
    }

    @Test
    @DisplayName("prazo de quem ja confirmou nao faz nada")
    void deadline_afterConfirm_noop() {
        pergunta();
        service.handleReply(PHONE, "sim");

        service.handleDeadline(task(AgendaTaskService.CONFIRMATION_DEADLINE));

        assertThat(ag.getConfirmationStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        verify(messenger, never()).send(eq(ADMIN), any(), eq("ADMIN"));
    }
}

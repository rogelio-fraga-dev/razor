package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfessionalAssignmentServiceTest {

    private static final LocalDate DATE = LocalDate.of(2030, 3, 15);
    private static final LocalTime TIME = LocalTime.of(10, 0);

    @Mock private ProfessionalRepository professionalRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ScheduleBlockService scheduleBlockService; // sem bloqueios por padrão (false)
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @InjectMocks private ProfessionalAssignmentService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Professional prof(UUID id) {
        return Professional.builder().id(id).barbershopId(TenantContext.getCurrent()).name("P-" + id).active(true).build();
    }

    private Appointment at(UUID profId, LocalTime time) {
        return Appointment.builder().professionalId(profId).appointmentDate(DATE).appointmentTime(time).build();
    }

    @Test
    @DisplayName("sem profissionais ativos, não atribui (null)")
    void noProfessionals_returnsNull() {
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());
        assertThat(service.assign(DATE, TIME, null)).isNull();
    }

    @Test
    @DisplayName("auto-atribui o barbeiro livre menos ocupado no dia")
    void auto_picksLeastBusyFreeProfessional() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof(a), prof(b)));
        // A tem 2 agendamentos no dia (nenhum às 10:00 -> livre no slot), B tem 0
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(a), eq(DATE)))
                .thenReturn(List.of(at(a, LocalTime.of(9, 0)), at(a, LocalTime.of(11, 0))));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(b), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, null)).isEqualTo(b); // menos ocupado
    }

    @Test
    @DisplayName("barbeiro de FOLGA não é atribuído, mesmo com a agenda vazia")
    void professionalOnLeave_isNotAssigned() {
        UUID folgando = UUID.randomUUID();
        UUID disponivel = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(prof(folgando), prof(disponivel)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), eq(DATE)))
                .thenReturn(List.of()); // ambos sem agendamento
        when(scheduleBlockService.isBlocked(eq(DATE), eq(TIME), eq(30), eq(folgando))).thenReturn(true);

        assertThat(service.assign(DATE, TIME, null)).isEqualTo(disponivel);
        // e nem sendo pedido explicitamente ele é escolhido
        assertThat(service.assign(DATE, TIME, folgando)).isEqualTo(disponivel);
    }

    @Test
    @DisplayName("respeita a escolha explícita quando o preferido está livre")
    void explicit_preferredHonoredWhenFree() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof(a), prof(b)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, a)).isEqualTo(a);
    }

    @Test
    @DisplayName("preferido ocupado no horário cai na auto-atribuição")
    void explicit_preferredBusy_fallsBackToAuto() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof(a), prof(b)));
        // A ocupado às 10:00, B livre
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(a), eq(DATE)))
                .thenReturn(List.of(at(a, TIME)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(b), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, a)).isEqualTo(b);
    }

    @Test
    @DisplayName("todos os barbeiros ocupados no horário -> null")
    void allBusy_returnsNull() {
        UUID a = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof(a)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(a), eq(DATE)))
                .thenReturn(List.of(at(a, TIME)));

        assertThat(service.assign(DATE, TIME, null)).isNull();
    }

    // ---------- serviços que atende (V37) ----------

    private final UUID corteId = UUID.randomUUID();
    private final UUID barbaId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private void givenCatalog() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(corteId).name("Corte de Cabelo").build(),
                ServiceOffering.builder().id(barbaId).name("Barba").build(),
                ServiceOffering.builder().id(comboId).name("Corte + Barba (Combo)").build()));
    }

    private Professional prof(UUID id, UUID... services) {
        Professional p = prof(id);
        p.setServiceIds(new java.util.HashSet<>(Set.of(services)));
        return p;
    }

    @Test
    @DisplayName("quem não faz barba não recebe barba, mesmo sendo o menos ocupado")
    void auto_skipsProfessionalWhoDoesNotDoTheService() {
        givenCatalog();
        UUID soCorte = UUID.randomUUID();
        UUID fazTudo = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(prof(soCorte, corteId), prof(fazTudo)));
        // soCorte está livre e vazio; fazTudo tem 2 agendamentos no dia
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(soCorte), eq(DATE)))
                .thenReturn(List.of());
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), eq(fazTudo), eq(DATE)))
                .thenReturn(List.of(at(fazTudo, LocalTime.of(9, 0)), at(fazTudo, LocalTime.of(11, 0))));

        assertThat(service.assign(DATE, TIME, null, "Barba")).isEqualTo(fazTudo);
        // "Corte e barba" escrito pela IA também exige barba
        assertThat(service.assign(DATE, TIME, null, "corte e barba")).isEqualTo(fazTudo);
        // e o combo, que ele não marcou
        assertThat(service.assign(DATE, TIME, null, "Corte + Barba (Combo)")).isEqualTo(fazTudo);
        // corte ele faz: volta a ganhar por estar menos ocupado
        assertThat(service.assign(DATE, TIME, null, "Corte")).isEqualTo(soCorte);
    }

    @Test
    @DisplayName("escolha explícita de quem não faz o serviço cai na auto-atribuição")
    void explicit_preferredWhoDoesNotDoTheService_fallsBack() {
        givenCatalog();
        UUID soCorte = UUID.randomUUID();
        UUID barbeiro = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(prof(soCorte, corteId), prof(barbeiro, corteId, barbaId)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, soCorte, "Barba")).isEqualTo(barbeiro);
        assertThat(service.assign(DATE, TIME, soCorte, "Corte de Cabelo")).isEqualTo(soCorte);
    }

    @Test
    @DisplayName("ninguém faz o serviço -> sem barbeiro (null), nunca alguém que não faz")
    void nobodyDoesTheService_returnsNull() {
        givenCatalog();
        UUID soCorte = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(prof(soCorte, corteId)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, soCorte, "Barba")).isNull();
    }

    @Test
    @DisplayName("lista vazia = atende todos; serviço fora do catálogo não restringe (sem regressão)")
    void emptyServices_meansAll_andUnknownServiceDoesNotRestrict() {
        givenCatalog();
        UUID semConfig = UUID.randomUUID();
        UUID soCorte = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(prof(semConfig), prof(soCorte, corteId)));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), eq(DATE)))
                .thenReturn(List.of());

        assertThat(service.assign(DATE, TIME, semConfig, "Barba")).isEqualTo(semConfig);
        // "Pigmentação" não existe no catálogo: ninguém perde o agendamento por isso
        assertThat(service.assign(DATE, TIME, soCorte, "Pigmentação")).isEqualTo(soCorte);
        // sem serviço informado (assinatura antiga), também não filtra
        assertThat(service.assign(DATE, TIME, soCorte)).isEqualTo(soCorte);
    }
}

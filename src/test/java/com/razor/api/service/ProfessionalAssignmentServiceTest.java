package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
}

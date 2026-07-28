package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.Professional;
import com.razor.api.model.ScheduleBlock;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@DisplayName("Taxa de ocupação das cadeiras")
class OccupancyServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private ScheduleBlockService scheduleBlockService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private PricingService pricingService;
    @InjectMocks private OccupancyService service;

    // Uma semana fechada: segunda 6/jul/2026 a domingo 12/jul/2026.
    private static final LocalDate SEG = LocalDate.of(2026, 7, 6);
    private static final LocalDate DOM = LocalDate.of(2026, 7, 12);

    private final UUID profId = UUID.randomUUID();

    private void givenShop(String opening, String closing, java.util.Set<DayOfWeek> days) {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(
                        "", "", "", null, "", "Barbearia", null, null, null,
                        opening, closing, days, null, null, null, null, null, null));
    }

    private void givenOneProfessional() {
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any()))
                .thenReturn(List.of(Professional.builder().id(profId).name("Rafael").active(true).build()));
    }

    private void givenNoBlocks() {
        when(scheduleBlockService.listAll()).thenReturn(List.of());
    }

    private void givenAppointments(Appointment... apps) {
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any()))
                .thenReturn(List.of(apps));
        when(pricingService.resolveDurations()).thenReturn(
                Map.of(PricingService.ServiceCategory.SIMPLES, 30,
                        PricingService.ServiceCategory.COMBO, 60));
        when(pricingService.categorize("Corte")).thenReturn(PricingService.ServiceCategory.SIMPLES);
        when(pricingService.categorize("Combo")).thenReturn(PricingService.ServiceCategory.COMBO);
    }

    private Appointment appt(String service, LocalDate date, UUID prof) {
        return Appointment.builder().service(service).appointmentDate(date)
                .appointmentTime(LocalTime.of(10, 0)).professionalId(prof)
                .paymentStatus(PaymentStatus.PENDENTE).build();
    }

    @Test
    @DisplayName("dia fechado não entra nas horas disponíveis")
    void closedDaysAreNotAvailable() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT); // seg-sáb
        givenOneProfessional();
        givenNoBlocks();
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        // 6 dias abertos x 10h = 60h. Contar o domingo daria 70h e derrubaria a taxa em ~14%
        // sem ninguém entender por quê.
        assertThat(out.openDays()).isEqualTo(6);
        assertThat(out.availableHours()).isEqualByComparingTo("60.0");
    }

    @Test
    @DisplayName("tenant que abre todo dia tem 7 dias disponíveis")
    void openEveryDay() {
        givenShop("09:00", "19:00", EnumSet.allOf(DayOfWeek.class));
        givenOneProfessional();
        givenNoBlocks();
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        assertThat(out.openDays()).isEqualTo(7);
        assertThat(out.availableHours()).isEqualByComparingTo("70.0");
    }

    @Test
    @DisplayName("horas vendidas usam a duração real do serviço, não a contagem de atendimentos")
    void bookedHoursUseRealDuration() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        givenNoBlocks();
        // 2 combos (60min) + 2 cortes (30min) = 3h, não "4 atendimentos".
        givenAppointments(
                appt("Combo", SEG, profId), appt("Combo", SEG, profId),
                appt("Corte", SEG, profId), appt("Corte", SEG, profId));

        var out = service.occupancy(SEG, DOM);

        assertThat(out.bookedHours()).isEqualByComparingTo("3.0");
        // 3h de 60h disponíveis
        assertThat(out.rate()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("folga de dia inteiro zera a jornada daquele dia")
    void fullDayBlockRemovesTheWholeDay() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        when(scheduleBlockService.listAll()).thenReturn(List.of(
                ScheduleBlock.builder().professionalId(profId).blockDate(SEG).build()));
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        // 6 dias abertos, um deles perdido inteiro: 50h em vez de 60h.
        assertThat(out.availableHours()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("almoço desconta só a faixa, não o dia todo")
    void partialBlockRemovesOnlyItsRange() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        // Almoço recorrente de segunda (weekday 1 na convenção 0=domingo da V16), 12h-13h.
        when(scheduleBlockService.listAll()).thenReturn(List.of(
                ScheduleBlock.builder().professionalId(profId).weekday((short) 1)
                        .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0)).build()));
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        // 60h menos 1h da segunda.
        assertThat(out.availableHours()).isEqualByComparingTo("59.0");
    }

    @Test
    @DisplayName("bloqueio fora do expediente não desconta hora que não existia")
    void blockOutsideJourneyDoesNotCount() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        // 20h-22h: a barbearia já está fechada, então não há o que descontar.
        when(scheduleBlockService.listAll()).thenReturn(List.of(
                ScheduleBlock.builder().professionalId(profId).blockDate(SEG)
                        .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0)).build()));
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        assertThat(out.availableHours()).isEqualByComparingTo("60.0");
    }

    @Test
    @DisplayName("agendamento sem barbeiro é contado à parte, não distribuído por chute")
    void unassignedAppointmentsAreReportedSeparately() {
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        givenNoBlocks();
        givenAppointments(appt("Corte", SEG, null), appt("Corte", SEG, profId));

        var out = service.occupancy(SEG, DOM);

        assertThat(out.unassignedAppointments()).isEqualTo(1);
        // Só o atribuído entra nas horas do barbeiro — atribuir o outro inventaria ocupação.
        assertThat(out.professionals()).singleElement()
                .satisfies(p -> assertThat(p.bookedHours()).isEqualByComparingTo("0.5"));
    }

    @Test
    @DisplayName("sem hora disponível, a taxa vem nula em vez de 0% ou divisão por zero")
    void noAvailableHours_nullRate() {
        // Período de um domingo só, numa barbearia que não abre domingo.
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        givenNoBlocks();
        givenAppointments();

        var out = service.occupancy(DOM, DOM);

        assertThat(out.openDays()).isZero();
        assertThat(out.availableHours()).isEqualByComparingTo("0.0");
        assertThat(out.rate()).isNull();
    }

    @Test
    @DisplayName("os barbeiros vêm do mais ocupado para o menos")
    void professionalsSortedByRateDesc() {
        UUID outro = UUID.randomUUID();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                Professional.builder().id(profId).name("Rafael").active(true).build(),
                Professional.builder().id(outro).name("Bruno").active(true).build()));
        givenShop("09:00", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenNoBlocks();
        givenAppointments(appt("Combo", SEG, outro), appt("Combo", SEG, outro), appt("Corte", SEG, profId));

        var out = service.occupancy(SEG, DOM);

        assertThat(out.professionals()).extracting(p -> p.professionalName())
                .containsExactly("Bruno", "Rafael");
        assertThat(out.professionals().get(0).rate())
                .isGreaterThan(out.professionals().get(1).rate());
    }

    @Test
    @DisplayName("horário de abertura inválido cai no padrão em vez de derrubar o relatório")
    void invalidOpeningTime_fallsBack() {
        givenShop("nove horas", "19:00", com.razor.api.util.WorkingDays.DEFAULT);
        givenOneProfessional();
        givenNoBlocks();
        givenAppointments();

        var out = service.occupancy(SEG, DOM);

        assertThat(out.availableHours()).isEqualByComparingTo(BigDecimal.valueOf(60.0));
    }
}

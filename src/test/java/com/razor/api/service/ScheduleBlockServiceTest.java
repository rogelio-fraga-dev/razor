package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.ScheduleBlock;
import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ScheduleBlockRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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

/** Folgas/bloqueios: quem incide em que dia, em qual faixa e para qual barbeiro. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleBlockServiceTest {

    private static final LocalDate QUINTA = LocalDate.of(2026, 7, 23); // quinta-feira
    private static final UUID DIEGO = UUID.randomUUID();
    private static final UUID RAFAEL = UUID.randomUUID();

    @Mock private ScheduleBlockRepository repository;
    @Mock private ProfessionalRepository professionalRepository;
    @InjectMocks private ScheduleBlockService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void given(ScheduleBlock... blocks) {
        when(repository.findByBarbershopId(any())).thenReturn(List.of(blocks));
    }

    private ScheduleBlock.ScheduleBlockBuilder base() {
        return ScheduleBlock.builder().id(UUID.randomUUID()).barbershopId(TenantContext.getCurrent());
    }

    @Test
    @DisplayName("folga do dia inteiro de um barbeiro bloqueia só ele")
    void fullDayBlock_onlyThatProfessional() {
        given(base().professionalId(DIEGO).blockDate(QUINTA).build());

        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, DIEGO)).isTrue();
        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, RAFAEL)).isFalse();
    }

    @Test
    @DisplayName("bloqueio sem profissional (feriado) atinge a barbearia inteira")
    void shopWideBlock_hitsEveryone() {
        given(base().blockDate(QUINTA).reason("Feriado").build());

        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, DIEGO)).isTrue();
        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, RAFAEL)).isTrue();
        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, null)).isTrue();
    }

    @Test
    @DisplayName("almoço 12:00-13:00 bloqueia só a faixa — 11:30 livre, 12:30 bloqueado")
    void lunchWindow_blocksOnlyItsRange() {
        given(base().blockDate(QUINTA).startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0))
                .reason("Almoço").build());

        assertThat(service.isBlocked(QUINTA, LocalTime.of(11, 0), 30, DIEGO)).isFalse();
        assertThat(service.isBlocked(QUINTA, LocalTime.of(12, 30), 30, DIEGO)).isTrue();
        assertThat(service.isBlocked(QUINTA, LocalTime.of(13, 0), 30, DIEGO)).isFalse(); // fim exclusivo
    }

    @Test
    @DisplayName("serviço longo que INVADE o bloqueio é barrado (combo de 60min às 11:30)")
    void longServiceOverlappingBlock_isBarred() {
        given(base().blockDate(QUINTA).startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0)).build());

        // 11:30 + 60min = 12:30 -> invade o almoço
        assertThat(service.isBlocked(QUINTA, LocalTime.of(11, 30), 60, DIEGO)).isTrue();
        // 11:30 + 30min = 12:00 -> encosta mas não invade
        assertThat(service.isBlocked(QUINTA, LocalTime.of(11, 30), 30, DIEGO)).isFalse();
    }

    @Test
    @DisplayName("folga recorrente por dia da semana incide toda semana, não em outros dias")
    void weeklyBlock_appliesOnMatchingWeekday() {
        // 4 = quinta (0=domingo)
        given(base().professionalId(DIEGO).weekday((short) 4).build());

        assertThat(service.isBlocked(QUINTA, LocalTime.of(10, 0), 30, DIEGO)).isTrue();
        assertThat(service.isBlocked(QUINTA.plusWeeks(1), LocalTime.of(10, 0), 30, DIEGO)).isTrue();
        assertThat(service.isBlocked(QUINTA.plusDays(1), LocalTime.of(10, 0), 30, DIEGO)).isFalse();
    }

    @Test
    @DisplayName("bloqueio pontual não vaza para outra data")
    void oneOffBlock_doesNotLeak() {
        given(base().blockDate(QUINTA).build());

        assertThat(service.isBlocked(QUINTA.plusDays(1), LocalTime.of(10, 0), 30, DIEGO)).isFalse();
    }

    @Test
    @DisplayName("validação: exige data OU dia da semana, e faixa de horário coerente")
    void create_validates() {
        assertThatThrownBy(() -> service.create(null, QUINTA, (short) 3, null, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null, null, null, null, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null, QUINTA, null, LocalTime.of(14, 0), LocalTime.of(13, 0), "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null, QUINTA, null, LocalTime.of(14, 0), null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create recusa profissional de outra barbearia (isolamento) e não salva")
    void create_professionalOfOtherShop_notFound() {
        UUID alheio = UUID.randomUUID();
        when(professionalRepository.findById(alheio)).thenReturn(Optional.of(
                Professional.builder().id(alheio).barbershopId(UUID.randomUUID()).name("X").build()));

        assertThatThrownBy(() -> service.create(alheio, QUINTA, null, null, null, "folga"))
                .isInstanceOf(NotFoundException.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("create aceita profissional do próprio tenant")
    void create_professionalOfThisShop_saves() {
        when(professionalRepository.findById(DIEGO)).thenReturn(Optional.of(
                Professional.builder().id(DIEGO).barbershopId(TenantContext.getCurrent()).name("Diego").build()));
        when(repository.save(any(ScheduleBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(DIEGO, QUINTA, null, null, null, "folga").getProfessionalId()).isEqualTo(DIEGO);
    }
}

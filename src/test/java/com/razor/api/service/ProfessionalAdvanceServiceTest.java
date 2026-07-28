package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.Professional;
import com.razor.api.model.ProfessionalAdvance;
import com.razor.api.repository.ProfessionalAdvanceRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/** Testes dos vales (V26): saida opcional do caixa e a soma que o acerto desconta. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfessionalAdvanceServiceTest {

    @Mock private ProfessionalAdvanceRepository advanceRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CashRegisterService cashRegisterService;

    @InjectMocks private ProfessionalAdvanceService service;

    private final UUID profId = UUID.randomUUID();
    private final LocalDate inicio = LocalDate.of(2026, 7, 1);
    private final LocalDate fim = LocalDate.of(2026, 7, 15);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenProfessional() {
        when(professionalRepository.findById(profId)).thenReturn(Optional.of(
                Professional.builder().id(profId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                        .name("Rafael").build()));
        when(advanceRepository.save(any(ProfessionalAdvance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProfessionalAdvance advance(UUID prof, String amount) {
        return ProfessionalAdvance.builder()
                .professionalId(prof).amount(new BigDecimal(amount)).advanceDate(inicio).build();
    }

    @Test
    @DisplayName("vale por fora do caixa não gera sangria")
    void grant_outsideCashRegister_createsNoMovement() {
        givenProfessional();

        ProfessionalAdvance saved = service.grant(profId, new BigDecimal("100.00"), inicio,
                false, "PIX", "Dono");

        assertThat(saved.getCashMovementId()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getAdvanceDate()).isEqualTo(inicio);
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("vale em dinheiro sai da gaveta como sangria")
    void grant_fromCashRegister_createsSangria() {
        givenProfessional();
        UUID movementId = UUID.randomUUID();
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(movementId).build());

        ProfessionalAdvance saved = service.grant(profId, new BigDecimal("100.00"), inicio,
                true, null, "Dono");

        assertThat(saved.getCashMovementId()).isEqualTo(movementId);
        verify(cashRegisterService).recordMovement(
                eq(CashMovementType.SANGRIA), isNull(), eq(new BigDecimal("100.00")), eq("Vale Rafael"));
    }

    @Test
    @DisplayName("sem caixa aberto, o vale em dinheiro é recusado inteiro")
    void grant_fromCashRegisterWithoutSession_isRejected() {
        givenProfessional();
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.grant(profId, new BigDecimal("100.00"), inicio, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caixa aberto");

        verify(advanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("data nula usa hoje")
    void grant_nullDate_usesToday() {
        givenProfessional();

        ProfessionalAdvance saved = service.grant(profId, new BigDecimal("50.00"), null, false, null, null);

        assertThat(saved.getAdvanceDate()).isEqualTo(AppointmentDates.today());
    }

    @Test
    @DisplayName("valor não positivo é recusado")
    void grant_nonPositiveAmount_isRejected() {
        givenProfessional();

        assertThatThrownBy(() -> service.grant(profId, BigDecimal.ZERO, inicio, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.grant(profId, new BigDecimal("-5"), inicio, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(advanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("barbeiro de outra barbearia não recebe vale")
    void grant_otherTenantProfessional_notFound() {
        when(professionalRepository.findById(profId)).thenReturn(Optional.of(
                Professional.builder().id(profId).barbershopId(UUID.randomUUID()).name("Intruso").build()));

        assertThatThrownBy(() -> service.grant(profId, new BigDecimal("10"), inicio, false, null, null))
                .isInstanceOf(NotFoundException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("soma os vales por barbeiro no período — é o que o acerto desconta")
    void advancedByProfessional_sumsPerProfessional() {
        UUID outro = UUID.randomUUID();
        when(advanceRepository.findByBarbershopIdAndAdvanceDateBetween(any(), eq(inicio), eq(fim)))
                .thenReturn(List.of(
                        advance(profId, "100.00"),
                        advance(profId, "50.00"),
                        advance(outro, "80.00")));

        Map<UUID, BigDecimal> vales = service.advancedByProfessional(inicio, fim);

        assertThat(vales.get(profId)).isEqualByComparingTo("150.00");
        assertThat(vales.get(outro)).isEqualByComparingTo("80.00");
    }
}

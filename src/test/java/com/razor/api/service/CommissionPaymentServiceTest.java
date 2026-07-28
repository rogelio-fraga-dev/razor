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
import com.razor.api.model.CommissionPayment;
import com.razor.api.model.Professional;
import com.razor.api.repository.CommissionPaymentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
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

/** Testes do acerto de comissao (V25): saida opcional do caixa e o que evita pagar duas vezes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionPaymentServiceTest {

    @Mock private CommissionPaymentRepository paymentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CashRegisterService cashRegisterService;

    @InjectMocks private CommissionPaymentService service;

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
        when(paymentRepository.save(any(CommissionPayment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CommissionPayment payment(UUID prof, String amount) {
        return CommissionPayment.builder()
                .professionalId(prof).amount(new BigDecimal(amount))
                .periodStart(inicio).periodEnd(fim).build();
    }

    // ---------- saída do caixa é opcional ----------

    @Test
    @DisplayName("pagamento por fora do caixa não gera sangria")
    void pay_outsideCashRegister_createsNoMovement() {
        givenProfessional();

        CommissionPayment saved = service.pay(profId, inicio, fim, new BigDecimal("500.00"),
                false, "PIX", "Dono");

        assertThat(saved.getCashMovementId()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("500.00");
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pagamento em dinheiro sai da gaveta como sangria")
    void pay_fromCashRegister_createsSangria() {
        givenProfessional();
        UUID movementId = UUID.randomUUID();
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(movementId).build());

        CommissionPayment saved = service.pay(profId, inicio, fim, new BigDecimal("500.00"),
                true, null, "Dono");

        assertThat(saved.getCashMovementId()).isEqualTo(movementId);
        verify(cashRegisterService).recordMovement(
                eq(CashMovementType.SANGRIA), isNull(), eq(new BigDecimal("500.00")), eq("Comissao Rafael"));
    }

    @Test
    @DisplayName("sem caixa aberto, o pagamento em dinheiro é recusado inteiro")
    void pay_fromCashRegisterWithoutSession_isRejected() {
        givenProfessional();
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.pay(profId, inicio, fim, new BigDecimal("500.00"), true, null, "Dono"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caixa aberto");

        verify(paymentRepository, never()).save(any());
    }

    // ---------- validação ----------

    @Test
    @DisplayName("valor não positivo é recusado")
    void pay_nonPositiveAmount_isRejected() {
        givenProfessional();

        assertThatThrownBy(() -> service.pay(profId, inicio, fim, BigDecimal.ZERO, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pay(profId, inicio, fim, new BigDecimal("-10"), false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pay(profId, inicio, fim, null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("período invertido é recusado")
    void pay_invertedPeriod_isRejected() {
        givenProfessional();

        assertThatThrownBy(() -> service.pay(profId, fim, inicio, new BigDecimal("10"), false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antes do inicio");
    }

    @Test
    @DisplayName("barbeiro de outra barbearia não recebe acerto")
    void pay_otherTenantProfessional_notFound() {
        when(professionalRepository.findById(profId)).thenReturn(Optional.of(
                Professional.builder().id(profId).barbershopId(UUID.randomUUID()).name("Intruso").build()));

        assertThatThrownBy(() -> service.pay(profId, inicio, fim, new BigDecimal("10"), false, null, null))
                .isInstanceOf(NotFoundException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    // ---------- o que evita pagar duas vezes ----------

    @Test
    @DisplayName("soma os acertos já pagos por barbeiro no período")
    void paidByProfessional_sumsPerProfessional() {
        UUID outro = UUID.randomUUID();
        when(paymentRepository.findByBarbershopIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
                any(), eq(inicio), eq(fim)))
                .thenReturn(List.of(
                        payment(profId, "300.00"),
                        payment(profId, "200.00"), // dois acertos parciais do mesmo barbeiro
                        payment(outro, "150.00")));

        Map<UUID, BigDecimal> pago = service.paidByProfessional(inicio, fim);

        assertThat(pago.get(profId)).isEqualByComparingTo("500.00");
        assertThat(pago.get(outro)).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("período sem acerto nenhum devolve mapa vazio")
    void paidByProfessional_noPayments_isEmpty() {
        when(paymentRepository.findByBarbershopIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
                any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.paidByProfessional(inicio, fim)).isEmpty();
    }
}

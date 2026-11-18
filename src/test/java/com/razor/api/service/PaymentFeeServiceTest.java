package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.PaymentMethodFee;
import com.razor.api.repository.PaymentMethodFeeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Taxas por forma de pagamento (V35). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentFeeServiceTest {

    @Mock private PaymentMethodFeeRepository repository;
    @InjectMocks private PaymentFeeService service;

    @Test
    @DisplayName("taxa é calculada sobre o valor e arredondada ao centavo")
    void feeOf_roundsToCents() {
        assertThat(PaymentFeeService.feeOf(new BigDecimal("59.90"), new BigDecimal("3.50")))
                .isEqualByComparingTo("2.10"); // 2,0965
        assertThat(PaymentFeeService.feeOf(new BigDecimal("59.90"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sem cadastro, toda forma com taxa aparece zerada; dinheiro nem aparece")
    void rates_defaultsToZero() {
        when(repository.findByBarbershopId(any())).thenReturn(List.of(PaymentMethodFee.builder()
                .paymentMethod(CashPaymentMethod.CREDITO).feePercent(new BigDecimal("3.50")).build()));

        Map<CashPaymentMethod, BigDecimal> rates = service.rates();

        assertThat(rates).containsOnlyKeys(CashPaymentMethod.PIX, CashPaymentMethod.DEBITO,
                CashPaymentMethod.CREDITO, CashPaymentMethod.OUTRO);
        assertThat(rates.get(CashPaymentMethod.CREDITO)).isEqualByComparingTo("3.50");
        assertThat(rates.get(CashPaymentMethod.PIX)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("dinheiro não aceita taxa e taxa fora de 0–99,99% é recusada")
    void update_rejectsInvalid() {
        assertThatThrownBy(() -> service.update(Map.of(CashPaymentMethod.DINHEIRO, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Dinheiro");
        assertThatThrownBy(() -> service.update(Map.of(CashPaymentMethod.CREDITO, new BigDecimal("100"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(Map.of(CashPaymentMethod.CREDITO, new BigDecimal("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("taxa zero remove o cadastro existente")
    void update_zeroDeletes() {
        PaymentMethodFee existing = PaymentMethodFee.builder()
                .paymentMethod(CashPaymentMethod.PIX).feePercent(new BigDecimal("1.00")).build();
        when(repository.findByBarbershopIdAndPaymentMethod(any(), any())).thenReturn(Optional.of(existing));

        service.update(Map.of(CashPaymentMethod.PIX, BigDecimal.ZERO));

        verify(repository).delete(existing);
        verify(repository, never()).save(any());
    }
}

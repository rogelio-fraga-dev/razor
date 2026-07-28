package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Coupon;
import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
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

/**
 * Testes do cartao fidelidade. O ponto central: zerar a cartela e emitir o cupom sao a mesma
 * operacao — a versao anterior zerava primeiro e engolia a excecao se a emissao falhasse,
 * deixando o cliente sem pontos e sem recompensa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FidelityServiceTest {

    private static final String PHONE = "5511999990000";

    @Mock private CustomerRepository customerRepository;
    @Mock private CouponService couponService;

    @InjectMocks private FidelityService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Customer givenCustomerWith(Integer pontos) {
        Customer c = Customer.builder()
                .id(UUID.randomUUID()).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .phoneNumber(PHONE).name("João").fidelityPoints(pontos).build();
        when(customerRepository.findByBarbershopIdAndPhoneNumber(any(UUID.class), eq(PHONE)))
                .thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        return c;
    }

    private Coupon coupon() {
        return Coupon.builder().id(UUID.randomUUID()).code("FIDELIDADE-ABC12")
                .discountValue(new BigDecimal("10")).build();
    }

    @Test
    @DisplayName("visita normal soma um ponto e não emite cupom")
    void registerVisit_incrementsPoints() {
        Customer c = givenCustomerWith(3);

        Optional<Coupon> recompensa = service.registerVisit(PHONE);

        assertThat(c.getFidelityPoints()).isEqualTo(4);
        assertThat(recompensa).isEmpty();
        verify(couponService, never()).issueFidelityReward(any());
    }

    @Test
    @DisplayName("cliente sem pontos ainda começa do zero corretamente")
    void registerVisit_nullPoints_startsAtOne() {
        Customer c = givenCustomerWith(null);

        service.registerVisit(PHONE);

        assertThat(c.getFidelityPoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("ao fechar a cartela, zera os pontos e emite o cupom")
    void registerVisit_completesCard_issuesCoupon() {
        Customer c = givenCustomerWith(CouponService.FIDELITY_TARGET_POINTS - 1);
        when(couponService.issueFidelityReward(eq(PHONE))).thenReturn(coupon());

        Optional<Coupon> recompensa = service.registerVisit(PHONE);

        assertThat(recompensa).isPresent();
        assertThat(c.getFidelityPoints()).isZero();
    }

    @Test
    @DisplayName("se a emissão do cupom falhar, os pontos NÃO são zerados")
    void registerVisit_couponFails_pointsArePreserved() {
        Customer c = givenCustomerWith(CouponService.FIDELITY_TARGET_POINTS - 1);
        when(couponService.issueFidelityReward(eq(PHONE)))
                .thenThrow(new RuntimeException("falha ao gravar o cupom"));

        assertThatThrownBy(() -> service.registerVisit(PHONE))
                .isInstanceOf(RuntimeException.class);

        // O cupom e emitido ANTES de zerar: a excecao sobe e o @Transactional desfaz tudo.
        // O cliente nao pode perder a cartela por causa de uma falha do sistema.
        assertThat(c.getFidelityPoints()).isEqualTo(CouponService.FIDELITY_TARGET_POINTS - 1);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("telefone sem cliente cadastrado não quebra nem emite nada")
    void registerVisit_unknownCustomer_isNoOp() {
        when(customerRepository.findByBarbershopIdAndPhoneNumber(any(UUID.class), eq(PHONE)))
                .thenReturn(Optional.empty());

        assertThat(service.registerVisit(PHONE)).isEmpty();
        verify(couponService, never()).issueFidelityReward(any());
        verify(customerRepository, never()).save(any());
    }
}

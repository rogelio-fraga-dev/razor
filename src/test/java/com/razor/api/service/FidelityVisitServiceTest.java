package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.model.Coupon;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Ponto de fidelidade no atendimento concluido (comanda fechada) e envio do cupom. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FidelityVisitServiceTest {

    private static final String PHONE = "5511999990000";

    @Mock private FidelityService fidelityService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BarbershopSettingsService barbershopSettingsService;

    @InjectMocks private FidelityVisitService service;

    @BeforeEach
    void settings() {
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
    }

    private Coupon coupon() {
        return Coupon.builder().id(UUID.randomUUID()).code("FIDELIDADE-ABC12")
                .discountType(Coupon.DiscountType.PERCENT).discountValue(new BigDecimal("10"))
                .validUntil(LocalDate.now().plusDays(90)).build();
    }

    @Test
    @DisplayName("sem cartela fechada, registra o ponto e nao manda nada")
    void visit_noReward_sendsNothing() {
        when(fidelityService.registerVisit(PHONE)).thenReturn(Optional.empty());

        service.registerCompletedVisit(PHONE);

        verify(fidelityService).registerVisit(PHONE);
        verifyNoInteractions(whatsAppService);
    }

    @Test
    @DisplayName("cartela fechada: o cupom vai ao cliente por envio SINCRONO")
    void visit_reward_sendsCouponSync() {
        when(fidelityService.registerVisit(PHONE)).thenReturn(Optional.of(coupon()));

        service.registerCompletedVisit(PHONE);

        verify(whatsAppService).sendTextMessageSync(eq(PHONE), contains("FIDELIDADE-ABC12"));
        verify(whatsAppService, never()).sendTextMessage(any(), any());
    }

    @Test
    @DisplayName("falha no envio do cupom nao lanca (o cupom continua valido e fica no log)")
    void visit_rewardSendFails_doesNotThrow() {
        when(fidelityService.registerVisit(PHONE)).thenReturn(Optional.of(coupon()));
        doThrow(new WhatsAppService.WhatsAppSendException("fora", new RuntimeException()))
                .when(whatsAppService).sendTextMessageSync(any(), any());

        assertThatCode(() -> service.registerCompletedVisit(PHONE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falha na fidelidade nao lanca — nao pode derrubar o fechamento da comanda")
    void visit_fidelityFails_doesNotThrow() {
        when(fidelityService.registerVisit(PHONE)).thenThrow(new RuntimeException("banco fora"));

        assertThatCode(() -> service.registerCompletedVisit(PHONE)).doesNotThrowAnyException();
        verifyNoInteractions(whatsAppService);
    }

    @Test
    @DisplayName("sem telefone, nao ha cliente para pontuar")
    void visit_blankPhone_isNoOp() {
        service.registerCompletedVisit(" ");

        verifyNoInteractions(fidelityService);
    }
}

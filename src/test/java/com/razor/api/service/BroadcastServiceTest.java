package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Customer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private WhatsAppMessenger messenger;
    @Mock private BarbershopSettingsService settings;

    private BroadcastService service;

    private final Customer ana = Customer.builder().phoneNumber("5511111111111").name("Ana").build();
    private final Customer bia = Customer.builder().phoneNumber("5522222222222").name("Bia").build();
    private final Customer admin = Customer.builder().phoneNumber("5599999999999").name("Dono").build();

    @BeforeEach
    void setUp() {
        service = new BroadcastService(messenger, settings, 0);
        when(settings.resolve()).thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
    }

    @Test
    @DisplayName("envio sincrono: so conta quem a Evolution aceitou, e as falhas aparecem no resultado")
    void deliver_countsOnlyDelivered_andFailures() {
        org.mockito.Mockito.lenient().doThrow(new WhatsAppService.WhatsAppSendException("fora", new RuntimeException()))
                .when(messenger).sendSync(eq(bia.getPhoneNumber()), any(), eq("DISPARO"));

        BroadcastService.Resultado r = service.deliver(List.of(ana, bia), "Promo!", true, null);

        assertThat(r.enviados()).containsExactly("Ana");
        assertThat(r.falhas()).isEqualTo(1);
        verify(messenger, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("pula o telefone de quem disparou e usa o cabecalho de promocao")
    void deliver_skipsAdmin_andUsesPromoHeader() {
        BroadcastService.Resultado r = service.deliver(List.of(ana, admin), "Promo!", true, admin.getPhoneNumber());

        assertThat(r.enviados()).containsExactly("Ana");
        verify(messenger, never()).sendSync(eq(admin.getPhoneNumber()), any(), any());
        verify(messenger).sendSync(eq(ana.getPhoneNumber()), startsWith("💈"), eq("DISPARO"));
    }
}

package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.WhatsAppConnectResponse;
import com.razor.api.dto.WhatsAppStatusResponse;
import com.razor.api.service.WhatsAppConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminWhatsAppControllerTest {

    @Mock private WhatsAppConnectionService service;
    @InjectMocks private AdminWhatsAppController controller;

    @Test
    @DisplayName("status delega ao serviço")
    void status_delegates() {
        WhatsAppStatusResponse resp = new WhatsAppStatusResponse("loja-wa", "open", true);
        when(service.status()).thenReturn(resp);

        assertThat(controller.status()).isSameAs(resp);
    }

    @Test
    @DisplayName("connect devolve o QR do serviço")
    void connect_returnsQr() {
        WhatsAppConnectResponse resp =
                new WhatsAppConnectResponse("loja-wa", "connecting", "data:image/png;base64,AAA", null);
        when(service.connect()).thenReturn(resp);

        assertThat(controller.connect().qrCodeBase64()).isEqualTo("data:image/png;base64,AAA");
    }

    @Test
    @DisplayName("disconnect responde 204 e chama o serviço")
    void disconnect_noContent() {
        assertThat(controller.disconnect().getStatusCode().value()).isEqualTo(204);
        verify(service).disconnect();
    }
}

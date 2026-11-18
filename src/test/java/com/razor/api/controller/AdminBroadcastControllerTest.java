package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.razor.api.dto.CustomerTagRequests;
import com.razor.api.service.BroadcastService;
import com.razor.api.service.CustomerSegmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminBroadcastControllerTest {

    @Test
    @DisplayName("disparo do painel sem tag nem status é recusado (não vira disparo para a base toda)")
    void send_withoutGroup_isRefused() {
        CustomerSegmentService segments = mock(CustomerSegmentService.class);
        BroadcastService broadcast = mock(BroadcastService.class);
        var controller = new AdminBroadcastController(segments, broadcast);

        assertThatThrownBy(() -> controller.send(new CustomerTagRequests.BroadcastRequest("Oi", null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Escolha um grupo");
        verify(segments, never()).recipients(any(), any());
        verify(broadcast, never()).deliverInBackground(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}

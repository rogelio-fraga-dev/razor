package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.BotSettingsPayload;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.service.BarbershopSettingsService;
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
class AdminBotSettingsControllerTest {

    @Mock private BarbershopSettingsService barbershopSettingsService;
    @InjectMocks private AdminBotSettingsController controller;

    @Test
    @DisplayName("get devolve os textos do bot do tenant")
    void get_returnsSettings() {
        when(barbershopSettingsService.getOrEmpty())
                .thenReturn(BarbershopSettings.builder().addressText("Rua X").build());

        BotSettingsPayload out = controller.get();

        assertThat(out.addressText()).isEqualTo("Rua X");
    }

    @Test
    @DisplayName("update repassa os campos para o serviço")
    void update_delegates() {
        when(barbershopSettingsService.update(eq("End"), eq("maps"), eq("Serv"), eq("sched"), eq("insta")))
                .thenReturn(BarbershopSettings.builder().addressText("End").build());

        controller.update(new BotSettingsPayload("End", "maps", "Serv", "sched", "insta"));

        verify(barbershopSettingsService).update("End", "maps", "Serv", "sched", "insta");
    }
}

package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.dto.BotSettingsPayload;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.service.BarbershopSettingsService;
import com.razor.api.service.WelcomeBriefService;
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
    @Mock private WelcomeBriefService welcomeBriefService;
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
    @DisplayName("update repassa o payload e dispara o resumo de boas-vindas")
    void update_delegates() {
        BotSettingsPayload payload = BotSettingsPayload.ofTexts("End", "maps", "Serv", "sched", "insta");
        BarbershopSettings saved = BarbershopSettings.builder().addressText("End").build();
        when(barbershopSettingsService.update(eq(payload))).thenReturn(saved);

        controller.update(payload, true);

        verify(barbershopSettingsService).update(payload);
        verify(welcomeBriefService).maybeSendAfterConfig(saved);
    }

    @Test
    @DisplayName("update com welcome=false grava sem disparar o resumo (salvamento por passo do modal)")
    void update_withoutWelcome_skipsBrief() {
        BotSettingsPayload payload = BotSettingsPayload.ofTexts("End", "maps", "Serv", "sched", "insta");
        BarbershopSettings saved = BarbershopSettings.builder().addressText("End").build();
        when(barbershopSettingsService.update(eq(payload))).thenReturn(saved);

        controller.update(payload, false);

        verify(barbershopSettingsService).update(payload);
        verifyNoInteractions(welcomeBriefService);
    }
}

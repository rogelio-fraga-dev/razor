package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.BarbershopAdmin;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.repository.BarbershopSettingsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link WelcomeBriefService}: envia o resumo uma unica vez, so quando ha admin
 * cadastrado, personaliza pelo nome do dono e nao reenvia depois de marcado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WelcomeBriefServiceTest {

    @Mock private TenantAdminService tenantAdminService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private BarbershopSettingsRepository settingsRepository;
    @Mock private WhatsAppService whatsAppService;

    @InjectMocks private WelcomeBriefService welcomeBriefService;

    private BarbershopSettings freshSettings() {
        return BarbershopSettings.builder().barbershopId(UUID.randomUUID()).build();
    }

    private void givenResolvedName() {
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", "", ""));
    }

    private BarbershopAdmin admin(String phone, String name) {
        return BarbershopAdmin.builder().id(UUID.randomUUID()).phone(phone).name(name).build();
    }

    @Test
    @DisplayName("1a configuracao com admin: envia a cada admin e marca como enviado")
    void firstConfig_withAdmins_sendsAndMarks() {
        BarbershopSettings s = freshSettings();
        when(tenantAdminService.listAdmins())
                .thenReturn(List.of(admin("5521970001111", "João Silva"), admin("5521970002222", null)));
        givenResolvedName();

        welcomeBriefService.maybeSendAfterConfig(s);

        verify(whatsAppService).sendTextMessage(eq("5521970001111"), any());
        verify(whatsAppService).sendTextMessage(eq("5521970002222"), any());
        assertThat(s.getWelcomeBriefSentAt()).isNotNull();
        verify(settingsRepository).save(s);
    }

    @Test
    @DisplayName("personaliza a mensagem com o primeiro nome do dono")
    void firstConfig_personalizesFirstName() {
        BarbershopSettings s = freshSettings();
        when(tenantAdminService.listAdmins()).thenReturn(List.of(admin("5521970001111", "João Silva")));
        givenResolvedName();

        welcomeBriefService.maybeSendAfterConfig(s);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(whatsAppService).sendTextMessage(eq("5521970001111"), msg.capture());
        assertThat(msg.getValue()).contains("João").doesNotContain("Silva");
    }

    @Test
    @DisplayName("ja enviado (timestamp preenchido): nao reenvia nem salva")
    void alreadySent_doesNothing() {
        BarbershopSettings s = freshSettings();
        s.setWelcomeBriefSentAt(LocalDateTime.now().minusDays(1));

        welcomeBriefService.maybeSendAfterConfig(s);

        verify(whatsAppService, never()).sendTextMessage(any(), any());
        verify(settingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("sem admin cadastrado: nao envia nem marca (tenta na proxima configuracao)")
    void noAdmins_doesNotSendOrMark() {
        BarbershopSettings s = freshSettings();
        when(tenantAdminService.listAdmins()).thenReturn(List.of());

        welcomeBriefService.maybeSendAfterConfig(s);

        verify(whatsAppService, never()).sendTextMessage(any(), any());
        verify(settingsRepository, never()).save(any());
        assertThat(s.getWelcomeBriefSentAt()).isNull();
    }

    @Test
    @DisplayName("settings nulo nao quebra")
    void nullSettings_noop() {
        welcomeBriefService.maybeSendAfterConfig(null);
        verify(whatsAppService, never()).sendTextMessage(any(), any());
        verify(settingsRepository, times(0)).save(any());
    }
}

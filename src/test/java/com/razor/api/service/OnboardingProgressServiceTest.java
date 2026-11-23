package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Barbershop;
import com.razor.api.model.Interaction.InteractionType;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Progresso do assistente guiado (V49)")
class OnboardingProgressServiceTest {

    @Mock private BarbershopRepository barbershopRepository;
    @Mock private InteractionRepository interactionRepository;

    private final UUID shopId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 0);
    private Barbershop shop;
    private OnboardingProgressService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(shopId);
        shop = Barbershop.builder().id(shopId).slug("ze").status(Barbershop.STATUS_TRIAL)
                .trialEndsAt(now.plusDays(14)).build();
        when(barbershopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(barbershopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new OnboardingProgressService(barbershopRepository, interactionRepository, clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("marca passos na ordem em que aconteceram, sem repetir")
    void markStep_accumulates() {
        service.markStep("horario");
        service.markStep("casa");
        var p = service.markStep("horario");

        assertThat(shop.getOnboardingStepsDone()).isEqualTo("horario,casa");
        assertThat(p.stepsDone()).containsExactly("horario", "casa");
        assertThat(p.completed()).isFalse();
        assertThat(p.steps()).first().isEqualTo("casa");
    }

    @Test
    @DisplayName("passo desconhecido é recusado e nada é gravado")
    void markStep_unknown() {
        assertThatThrownBy(() -> service.markStep("pagamento")).isInstanceOf(IllegalArgumentException.class);
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("concluir grava a data uma vez só")
    void complete_once() {
        assertThat(service.complete().completed()).isTrue();
        assertThat(shop.getOnboardingCompletedAt()).isEqualTo(now);
        shop.setOnboardingCompletedAt(now.minusDays(1));
        service.complete();
        assertThat(shop.getOnboardingCompletedAt()).isEqualTo(now.minusDays(1));
    }

    @Test
    @DisplayName("passo que saiu do assistente some da leitura")
    void parse_ignoresUnknown() {
        assertThat(OnboardingProgressService.parse("casa,velho, site")).containsExactly("casa", "site");
        assertThat(OnboardingProgressService.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("teste do bot: mensagem recente recebida e respondida")
    void botTest_receivedAndReplied() {
        when(interactionRepository.findLastTimestamp(shopId, InteractionType.USER)).thenReturn(now.minusMinutes(2));
        when(interactionRepository.findLastTimestamp(shopId, InteractionType.BOT)).thenReturn(now.minusMinutes(1));

        var t = service.botTest();
        assertThat(t.received()).isTrue();
        assertThat(t.replied()).isTrue();
    }

    @Test
    @DisplayName("teste do bot: mensagem velha não conta; resposta anterior à mensagem não é resposta")
    void botTest_oldOrUnanswered() {
        when(interactionRepository.findLastTimestamp(shopId, InteractionType.USER)).thenReturn(now.minusHours(2));
        assertThat(service.botTest().received()).isFalse();

        when(interactionRepository.findLastTimestamp(shopId, InteractionType.USER)).thenReturn(now.minusMinutes(1));
        when(interactionRepository.findLastTimestamp(shopId, InteractionType.BOT)).thenReturn(now.minusMinutes(5));
        var t = service.botTest();
        assertThat(t.received()).isTrue();
        assertThat(t.replied()).isFalse();
    }
}

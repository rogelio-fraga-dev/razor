package com.razor.api.service;

import com.razor.api.platform.PlatformShopsServiceTestAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do feedback do teste grátis e do relatório do console (26/11). */
class TrialFeedbackRulesTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 2, 10, 0);
    private static final LocalDateTime END = START.plusDays(14);

    @Test
    @DisplayName("etapa devida: nada antes do 3º dia; D3, depois D7, depois FIM; respondida não volta")
    void dueStage() {
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(2), List.of())).isEmpty();
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(3), List.of())).isEqualTo(Optional.of("D3"));
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(8), List.of())).isEqualTo(Optional.of("D3"));
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(8), List.of("D3"))).isEqualTo(Optional.of("D7"));
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(13), List.of("D3", "D7"))).isEqualTo(Optional.of("FIM"));
        assertThat(TrialFeedbackService.dueStage(START, END, START.plusDays(13), List.of("D3", "D7", "FIM"))).isEmpty();
        assertThat(TrialFeedbackService.dueStage(null, END, START, List.of())).isEmpty();
    }

    @Test
    @DisplayName("relatório: erros agrupados sem número/telefone, dados mascarados e custo da IA")
    void reportHelpers() {
        assertThat(PlatformShopsServiceTestAccess.signature("Falha no lembrete do cliente 5511999998888 (tenant 3f2b1c4d-1111-2222-3333-444455556666)"))
                .isEqualTo("Falha no lembrete do cliente # (tenant #id)");
        assertThat(PlatformShopsServiceTestAccess.mask("cliente 5511999998888 e joao.silva@gmail.com"))
                .doesNotContain("99999888").doesNotContain("joao.silva@");
        assertThat(PlatformShopsServiceTestAccess.cost("gpt-4o-mini", 1_000_000, 100_000, 10)).isEqualTo("0.21");
        assertThat(PlatformShopsServiceTestAccess.tempPassword()).startsWith("razor-").hasSize(16);
    }
}

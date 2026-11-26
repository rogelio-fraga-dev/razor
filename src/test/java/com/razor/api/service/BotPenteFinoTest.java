package com.razor.api.service;

import com.razor.api.model.ServiceOffering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras criadas no pente fino do bot de 25/11 (simulação em scripts/bot-sim.py). */
class BotPenteFinoTest {

    private static ServiceOffering svc(String name, String price) {
        return ServiceOffering.builder().id(UUID.randomUUID()).name(name).price(new BigDecimal(price)).active(true).build();
    }

    private final ServiceOffering corte = svc("Corte de Cabelo", "45.00");
    private final ServiceOffering barba = svc("Barba", "35.00");
    private final ServiceOffering combo = svc("Corte + Barba (Combo)", "70.00");
    private final ServiceOffering visagista = svc("Corte Visagista", "90.00");
    private final ServiceOffering sobrancelha = svc("Sobrancelha", "20.00");
    private final List<ServiceOffering> catalogo = List.of(corte, barba, combo, visagista, sobrancelha);

    private List<String> nomes(String texto) {
        return ServiceCapability.mentionedLoosely(texto, catalogo).stream().map(ServiceOffering::getName).toList();
    }

    @Test
    @DisplayName("jeitos comuns de pedir corte viram o corte simples, nunca o visagista")
    void corteDoDiaADia() {
        assertThat(nomes("quero cortar o cabelo")).containsExactly("Corte de Cabelo");
        assertThat(nomes("corte na maquina")).containsExactly("Corte de Cabelo");
        assertThat(nomes("quero marcar um corte")).containsExactly("Corte de Cabelo");
        assertThat(nomes("degradê amanhã")).containsExactly("Corte de Cabelo");
    }

    @Test
    @DisplayName("nome exato continua valendo")
    void nomeExato() {
        assertThat(nomes("quero o corte visagista")).containsExactly("Corte Visagista");
        assertThat(nomes("sobrancelha")).containsExactly("Sobrancelha");
        assertThat(nomes("tem horário amanhã?")).isEmpty();
    }

    @Test
    @DisplayName("corte e barba vira o combo quando o catálogo tem um")
    void corteEBarbaViraCombo() {
        assertThat(nomes("quero corte e barba sexta")).containsExactly("Corte + Barba (Combo)");
        assertThat(nomes("cortar o cabelo e fazer a barba")).containsExactly("Corte + Barba (Combo)");
        List<ServiceOffering> semCombo = List.of(corte, barba);
        assertThat(ServiceCapability.mentionedLoosely("corte e barba", semCombo)).containsExactly(corte, barba);
    }

    @Test
    @DisplayName("dia pedido em português do dia a dia")
    void diaPedido() {
        LocalDate quarta = LocalDate.of(2026, 11, 25);
        assertThat(RequestedDate.find("amanhã às 10h", quarta)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(RequestedDate.find("depois de amanhã de tarde", quarta)).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(RequestedDate.find("corte sexta às 15h", quarta)).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(RequestedDate.find("pode ser quarta", quarta)).isEqualTo(quarta);
        assertThat(RequestedDate.find("dia 30/11", quarta)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(RequestedDate.find("quero cortar dia 10 de dezembro", quarta)).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(RequestedDate.find("então 30/02", quarta)).isNull();
        assertThat(RequestedDate.find("dia 31", quarta)).isEqualTo(LocalDate.of(2026, 12, 31)); // novembro não tem 31
        assertThat(RequestedDate.find("oi", quarta)).isNull();
    }

    @Test
    @DisplayName("frase de desistência só quando o cliente desistiu")
    void desistencia() {
        assertThat(CustomerConversationService.asksToAbort("cancelar")).isTrue();
        assertThat(CustomerConversationService.asksToAbort("deixa pra lá")).isTrue();
        assertThat(CustomerConversationService.asksToAbort("não quero mais")).isTrue();
        assertThat(CustomerConversationService.asksToAbort("👍")).isFalse();
        assertThat(CustomerConversationService.asksToAbort("1")).isFalse();
        assertThat(CustomerConversationService.asksToAbort("Rafael")).isFalse();
        assertThat(CustomerConversationService.isAbortReply("Sem problemas! O atendimento foi cancelado.")).isTrue();
    }

    @Test
    @DisplayName("pedido de cancelar o horário marcado vai para o cancelamento de verdade")
    void cancelarHorarioMarcado() {
        assertThat(CustomerConversationService.wantsToCancelAppointment("quero cancelar meu horário")).isTrue();
        assertThat(CustomerConversationService.wantsToCancelAppointment("desmarcar")).isTrue();
        assertThat(CustomerConversationService.wantsToCancelAppointment("preciso desmarcar o corte de sábado")).isTrue();
        assertThat(CustomerConversationService.wantsToCancelAppointment("cancela minha reserva")).isTrue();
        // "cancelar" sozinho no meio de um agendamento é desistir da conversa, não do horário.
        assertThat(CustomerConversationService.wantsToCancelAppointment("cancelar")).isFalse();
        assertThat(CustomerConversationService.wantsToCancelAppointment("qual a política de cancelamento?")).isFalse();
    }

    @Test
    @DisplayName("pedido de trocar o horário é reconhecido")
    void remarcar() {
        assertThat(CustomerConversationService.wantsToReschedule("quero mudar meu horário para segunda")).isTrue();
        assertThat(CustomerConversationService.wantsToReschedule("dá pra remarcar?")).isTrue();
        assertThat(CustomerConversationService.wantsToReschedule("posso trocar meu dia?")).isTrue();
        assertThat(CustomerConversationService.wantsToReschedule("quero trocar de barbeiro")).isFalse();
    }

    @Test
    @DisplayName("plano piloto desliga cartão fidelidade, clube e pacotes; sem plano tudo liberado")
    void planoPiloto() {
        assertThat(PlanFeatures.disabledFor("PILOTO")).containsExactlyInAnyOrder(
                PlanFeatures.Feature.LOYALTY, PlanFeatures.Feature.CLUB, PlanFeatures.Feature.PACKAGES);
        assertThat(PlanFeatures.disabledFor("piloto")).hasSize(3);
        assertThat(PlanFeatures.disabledFor(null)).isEmpty();
        assertThat(PlanFeatures.disabledFor("EQUIPE")).isEmpty();
        assertThat(MenuOptions.getMenuAsText("Navalha", 4, false)).contains("(em breve)").doesNotContain("4 pontos");
        assertThat(MenuOptions.getMenuAsText("Navalha", 4)).contains("4 pontos");
    }
}

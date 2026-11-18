package com.razor.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link AdminCommandService}: menu de OPERACAO rapida do WhatsApp
 * (agenda, disparos, pausar/retomar/apagar) e a maquina de estados do admin.
 * Financeiro/comissoes redirecionam para o painel web (principio 6 do roadmap).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCommandServiceTest {

    private static final String ADMIN = "5511000000000";

    @Mock private CustomerService customerService;
    @Mock private CustomerRepository customerRepository;
    @Mock private InteractionRepository interactionRepository;
    @Mock private OpenAIService openAIService;
    @Mock private AgendaService agendaService;
    @Mock private RazorProperties properties;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private WhatsAppMessenger messenger;
    @Mock private EvolutionMediaService evolutionMediaService;
    @Mock private BarbershopSettingsService barbershopSettingsService;

    // Store REAL (nao mock): a maquina de estados da conversa precisa gravar/ler de verdade.
    @org.mockito.Spy
    private com.razor.api.state.StateStore stateStore =
            new com.razor.api.state.InMemoryStateStore(new com.fasterxml.jackson.databind.ObjectMapper());

    @InjectMocks private AdminCommandService service;

    private EvolutionWebhookDTO adminText(String text) {
        EvolutionWebhookDTO w = Mockito.mock(EvolutionWebhookDTO.class);
        when(w.getMessageText()).thenReturn(text);
        return w; // hasDocument/hasImage/hasAudio default false -> caminho de texto
    }

    @Test
    @DisplayName("'menu' envia o painel de operação rápida do admin")
    void menu_sendsFullMenu() {
        service.processAdminMessage(adminText("menu"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("PAINEL DA BARBEARIA")), eq("ADMIN"));
    }

    @Test
    @DisplayName("opção 1 envia a agenda do dia (operação, não financeiro)")
    void option1_sendsDailyAgenda() {
        when(agendaService.getPainelAgendamentosDetalhado(0, 0, 0)).thenReturn("AGENDA_DO_DIA");
        when(customerService.getPausedPhones()).thenReturn(List.of());
        service.processAdminMessage(adminText("1"), ADMIN);
        verify(messenger).send(eq(ADMIN), eq("AGENDA_DO_DIA"), eq("ADMIN"));
    }

    @Test
    @DisplayName("'financeiro' redireciona para o painel web (sem relatório no WhatsApp)")
    void financeiro_redirectsToPanel() {
        service.processAdminMessage(adminText("resumo financeiro"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("painel")), eq("ADMIN"));
        verify(agendaService, never()).getResumoFinanceiroDetalhado();
        verify(agendaService, never()).getResumoFinanceiroDaSemana();
    }

    @Test
    @DisplayName("'comissões' redireciona para o painel web")
    void comissoes_redirectsToPanel() {
        service.processAdminMessage(adminText("comissões"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("painel")), eq("ADMIN"));
    }

    @Test
    @DisplayName("listar clientes (8) com base vazia informa que não há ninguém salvo")
    void listClients_empty() {
        when(customerRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        service.processAdminMessage(adminText("8"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Nenhum cliente salvo")), eq("ADMIN"));
    }

    @Test
    @DisplayName("fluxo pausar (6): pede o telefone e, no 2º passo, pausa o cliente por 60min")
    void pauseFlow_twoSteps() {
        // passo 1: entra no estado PAUSAR
        service.processAdminMessage(adminText("6"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Pausar Robô")), eq("ADMIN"));

        // passo 2: informa o telefone (com formatação) -> pausa só os dígitos
        service.processAdminMessage(adminText("11 99998-8888"), ADMIN);
        verify(customerService).pauseCustomer("11999988888", 60);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("silenciado")), eq("ADMIN"));
    }

    @Test
    @DisplayName("'cancelar' dentro de um estado volta ao menu principal")
    void cancelDuringState_returnsToMenu() {
        service.processAdminMessage(adminText("6"), ADMIN); // entra em PAUSAR
        service.processAdminMessage(adminText("cancelar"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("PAINEL DA BARBEARIA")), eq("ADMIN"));
    }

    @Test
    @DisplayName("fluxo apagar (9): lista clientes e, no 2º passo, remove o escolhido")
    void deleteFlow_twoSteps() {
        UUID id = UUID.randomUUID();
        Customer c = Customer.builder().id(id).phoneNumber("5511777776666").name("Zé").build();
        when(customerRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(c));
        when(customerRepository.findByBarbershopIdAndPhoneNumber(any(UUID.class), eq("5511777776666")))
                .thenReturn(Optional.of(c));

        // passo 1: entra no estado APAGAR_CONTATO e lista
        service.processAdminMessage(adminText("9"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Apagar Cliente")), eq("ADMIN"));

        // passo 2: escolhe a opção 1 -> apaga interações e cliente
        service.processAdminMessage(adminText("1"), ADMIN);
        verify(interactionRepository).deleteByCustomerId(id);
        verify(customerRepository).delete(c);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("apagado")), eq("ADMIN"));
    }

    @Test
    @DisplayName("texto não reconhecido ENSINA os comandos por exemplo (não só 'digite menu')")
    void unknownText_teachesByExample() {
        service.processAdminMessage(adminText("blablabla xyz"), ADMIN);
        verify(messenger).send(eq(ADMIN),
                argThat(m -> m.contains("Não peguei") && m.contains("agenda de hoje") && m.contains("menu")),
                eq("ADMIN"));
        verify(agendaService, never()).getResumoFinanceiroDetalhado();
    }

    @Test
    @DisplayName("'oi' responde com o resumo do dia + comandos naturais (sem exigir 'menu')")
    void greeting_respondsWithBriefing() {
        when(agendaService.getAgendamentosHojeCount()).thenReturn(3);

        service.processAdminMessage(adminText("oi"), ADMIN);

        verify(messenger).send(eq(ADMIN),
                argThat(m -> m.contains("Fala, chefe") && m.contains("3") && m.contains("agenda de hoje")),
                eq("ADMIN"));
    }

    @Test
    @DisplayName("fala natural funciona sem o menu: 'manda um aviso' abre o disparo")
    void naturalPhrase_startsBroadcast() {
        service.processAdminMessage(adminText("manda um aviso"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Disparo de Avisos")), eq("ADMIN"));
    }

    @Test
    @DisplayName("relatório do disparo conta só quem recebeu de fato (envio síncrono) e informa as falhas")
    void broadcast_reportCountsOnlyDelivered() {
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
        var ok = com.razor.api.model.Customer.builder().phoneNumber("5511111111111").name("Ana").build();
        var falha = com.razor.api.model.Customer.builder().phoneNumber("5522222222222").name("Bia").build();
        when(customerRepository.findByBarbershopId(any())).thenReturn(java.util.List.of(ok, falha));
        Mockito.doThrow(new WhatsAppService.WhatsAppSendException("fora", new RuntimeException()))
                .when(messenger).sendSync(eq("5522222222222"), any(), eq("DISPARO"));

        service.performBroadcast(ADMIN, "Promo!", false);

        verify(messenger).send(eq(ADMIN),
                argThat(m -> m.contains("Relatório de Disparo") && m.contains("Total alcançado: 1")
                        && m.contains("Falharam: 1") && m.contains("Ana") && !m.contains("Bia")),
                eq("ADMIN"));
    }
}

package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Testes do {@link AdminCommandService} (Fase 0.4): roteamento do menu de gestao
 * e a maquina de estados do admin (pausar/apagar/cancelar), dirigida por chamadas
 * sequenciais na mesma instancia. Sem depender de banco/WhatsApp.
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

    @InjectMocks private AdminCommandService service;

    private EvolutionWebhookDTO adminText(String text) {
        EvolutionWebhookDTO w = Mockito.mock(EvolutionWebhookDTO.class);
        when(w.getMessageText()).thenReturn(text);
        return w; // hasDocument/hasImage/hasAudio default false -> caminho de texto
    }

    @Test
    @DisplayName("'menu' envia o painel de gestão do admin")
    void menu_sendsFullMenu() {
        service.processAdminMessage(adminText("menu"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("PAINEL DA BARBEARIA")), eq("ADMIN"));
    }

    @Test
    @DisplayName("opção 1 envia o resumo financeiro do dia")
    void option1_sendsFinancialSummary() {
        when(agendaService.getResumoFinanceiroDetalhado()).thenReturn("RESUMO_FINANCEIRO");
        service.processAdminMessage(adminText("1"), ADMIN);
        verify(messenger).send(eq(ADMIN), eq("RESUMO_FINANCEIRO"), eq("ADMIN"));
    }

    @Test
    @DisplayName("listar clientes com base vazia informa que não há ninguém salvo")
    void listClients_empty() {
        when(customerRepository.findAll()).thenReturn(List.of());
        service.processAdminMessage(adminText("10"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Nenhum cliente salvo")), eq("ADMIN"));
    }

    @Test
    @DisplayName("fluxo pausar: pede o telefone e, no 2º passo, pausa o cliente por 60min")
    void pauseFlow_twoSteps() {
        // passo 1: entra no estado PAUSAR
        service.processAdminMessage(adminText("8"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Pausar Robô")), eq("ADMIN"));

        // passo 2: informa o telefone (com formatação) -> pausa só os dígitos
        service.processAdminMessage(adminText("11 99998-8888"), ADMIN);
        verify(customerService).pauseCustomer("11999988888", 60);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("silenciado")), eq("ADMIN"));
    }

    @Test
    @DisplayName("'cancelar' dentro de um estado volta ao menu principal")
    void cancelDuringState_returnsToMenu() {
        service.processAdminMessage(adminText("8"), ADMIN); // entra em PAUSAR
        service.processAdminMessage(adminText("cancelar"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("PAINEL DA BARBEARIA")), eq("ADMIN"));
    }

    @Test
    @DisplayName("fluxo apagar: lista clientes e, no 2º passo, remove o escolhido")
    void deleteFlow_twoSteps() {
        UUID id = UUID.randomUUID();
        Customer c = Customer.builder().id(id).phoneNumber("5511777776666").name("Zé").build();
        when(customerRepository.findAll()).thenReturn(List.of(c));
        when(customerRepository.findByPhoneNumber("5511777776666")).thenReturn(Optional.of(c));

        // passo 1: entra no estado APAGAR_CONTATO e lista
        service.processAdminMessage(adminText("11"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Apagar Cliente")), eq("ADMIN"));

        // passo 2: escolhe a opção 1 -> apaga interações e cliente
        service.processAdminMessage(adminText("1"), ADMIN);
        verify(interactionRepository).deleteByCustomerId(id);
        verify(customerRepository).delete(c);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("apagado")), eq("ADMIN"));
    }

    @Test
    @DisplayName("texto livre não reconhecido responde com a dica de digitar 'menu'")
    void unknownText_promptsMenu() {
        service.processAdminMessage(adminText("blablabla xyz"), ADMIN);
        verify(messenger).send(eq(ADMIN), argThat(m -> m.contains("Digite *menu*")), eq("ADMIN"));
        verify(agendaService, never()).getResumoFinanceiroDetalhado();
    }
}

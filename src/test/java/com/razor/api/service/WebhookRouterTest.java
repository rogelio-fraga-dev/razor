package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.tenant.TenantResolver;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
 * Testes do {@link WebhookRouter} (Fase 1, increment 2): triagem/dedup, roteamento
 * admin vs cliente e o ciclo de vida do {@link TenantContext} (resolvido no início,
 * disponível durante o processamento e limpo ao final).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookRouterTest {

    private static final String CLIENTE = "5511999990000";

    @Mock private CustomerService customerService;
    @Mock private InteractionRepository interactionRepository;
    @Mock private AdminCommandService adminCommandService;
    @Mock private CustomerConversationService customerConversationService;
    @Mock private TenantResolver tenantResolver;
    @Mock private TenantAdminService tenantAdminService;

    @InjectMocks private WebhookRouter router;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EvolutionWebhookDTO webhook(String instance, String phone, String msgId) {
        EvolutionWebhookDTO w = Mockito.mock(EvolutionWebhookDTO.class);
        EvolutionWebhookDTO.DataDTO data = Mockito.mock(EvolutionWebhookDTO.DataDTO.class);
        EvolutionWebhookDTO.KeyDTO key = Mockito.mock(EvolutionWebhookDTO.KeyDTO.class);
        when(w.getInstance()).thenReturn(instance);
        when(w.isGroupChat()).thenReturn(false);
        when(w.getPhoneNumber()).thenReturn(phone);
        when(w.getData()).thenReturn(data);
        when(data.getPushName()).thenReturn("User");
        when(data.getKey()).thenReturn(key);
        when(key.getFromMe()).thenReturn(false);
        when(key.getId()).thenReturn(msgId);
        return w;
    }

    @Test
    @DisplayName("mensagem de cliente vai para o CustomerConversationService com o tenant resolvido")
    void customerMessage_routesWithTenant() {
        UUID shopId = UUID.randomUUID();
        when(tenantResolver.resolveByInstance("inst")).thenReturn(shopId);
        when(interactionRepository.existsByMessageId("m1")).thenReturn(false);
        when(tenantAdminService.isAdmin(CLIENTE)).thenReturn(false); // nao e admin
        when(customerService.isCustomerPaused(CLIENTE)).thenReturn(false);

        AtomicReference<UUID> tenantDuring = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuring.set(TenantContext.getCurrent());
            return null;
        }).when(customerConversationService).processCustomerMessage(any(), eq(CLIENTE));

        router.processWebhook(webhook("inst", CLIENTE, "m1"));

        verify(customerConversationService).processCustomerMessage(any(), eq(CLIENTE));
        verify(adminCommandService, never()).processAdminMessage(any(), any());
        assertThat(tenantDuring.get()).isEqualTo(shopId);
        assertThat(TenantContext.isSet()).isFalse(); // limpo ao final
    }

    @Test
    @DisplayName("mensagem de um número admin vai para o AdminCommandService")
    void adminMessage_routesToAdmin() {
        String adminPhone = "5511988887777";
        when(tenantResolver.resolveByInstance(any())).thenReturn(TenantContext.DEFAULT_BARBERSHOP_ID);
        when(interactionRepository.existsByMessageId("m2")).thenReturn(false);
        when(tenantAdminService.isAdmin(adminPhone)).thenReturn(true);

        router.processWebhook(webhook("inst", adminPhone, "m2"));

        verify(adminCommandService).processAdminMessage(any(), eq(adminPhone));
        verify(customerConversationService, never()).processCustomerMessage(any(), any());
    }

    @Test
    @DisplayName("mensagem duplicada é ignorada e o tenant é limpo mesmo assim")
    void duplicateMessage_ignored() {
        when(tenantResolver.resolveByInstance(any())).thenReturn(TenantContext.DEFAULT_BARBERSHOP_ID);
        when(interactionRepository.existsByMessageId("m3")).thenReturn(true); // duplicada

        router.processWebhook(webhook("inst", CLIENTE, "m3"));

        verify(customerConversationService, never()).processCustomerMessage(any(), any());
        verify(adminCommandService, never()).processAdminMessage(any(), any());
        assertThat(TenantContext.isSet()).isFalse();
    }
}

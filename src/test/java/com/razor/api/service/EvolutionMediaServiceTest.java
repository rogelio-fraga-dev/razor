package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.config.RazorProperties;
import com.razor.api.tenant.TenantResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Busca de midia na Evolution: o messageId (entrada externa) nunca altera o JSON enviado. */
@ExtendWith(MockitoExtension.class)
class EvolutionMediaServiceTest {

    @Mock private RazorProperties properties;
    @Mock private TenantResolver tenantResolver;

    @InjectMocks private EvolutionMediaService service;

    @Test
    @DisplayName("SEGURANÇA: messageId com aspas/JSON é recusado antes de qualquer chamada HTTP")
    void maliciousMessageId_isRejected() {
        assertThat(service.fetchBase64("abc\"},\"evil\":{\"x\":\"1")).isNull();
        assertThat(service.fetchBase64("")).isNull();
        assertThat(service.fetchBase64(null)).isNull();
        assertThat(service.fetchBase64("a".repeat(129))).isNull();

        verifyNoInteractions(properties, tenantResolver);
    }

    @Test
    @DisplayName("payload é montado pelo Jackson com o id escapado no lugar certo")
    void payload_isBuiltByJackson() throws Exception {
        String json = service.buildPayload("3EB0ABC_123-x");

        var node = new ObjectMapper().readTree(json);
        assertThat(node.at("/message/key/id").asText()).isEqualTo("3EB0ABC_123-x");

        // mesmo um valor hostil sai escapado (defesa em profundidade alem da validacao)
        var hostil = new ObjectMapper().readTree(service.buildPayload("a\"b"));
        assertThat(hostil.at("/message/key/id").asText()).isEqualTo("a\"b");
        assertThat(hostil.size()).isEqualTo(1);
    }
}

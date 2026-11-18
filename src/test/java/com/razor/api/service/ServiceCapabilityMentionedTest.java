package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.ServiceOffering;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Serviços do catálogo citados numa frase do cliente")
class ServiceCapabilityMentionedTest {

    private static ServiceOffering s(String name) {
        return ServiceOffering.builder().id(UUID.randomUUID()).name(name).build();
    }

    private final ServiceOffering corte = s("Corte");
    private final ServiceOffering barba = s("Barba");
    private final ServiceOffering combo = s("Corte e Barba");
    private final ServiceOffering sobrancelha = s("Sobrancelha");
    private final List<ServiceOffering> catalogo = List.of(corte, barba, combo, sobrancelha);

    private List<String> nomes(String texto) {
        return ServiceCapability.mentioned(texto, catalogo).stream().map(ServiceOffering::getName).toList();
    }

    @Test
    @DisplayName("acha o serviço sem caixa nem acento, como palavra inteira")
    void findsWholeWords() {
        assertThat(nomes("Quero fazer a BARBA amanhã")).containsExactly("Barba");
        assertThat(nomes("tem horário pra sobrancelha?")).containsExactly("Sobrancelha");
        assertThat(nomes("a Bárbara vem junto")).isEmpty();
    }

    @Test
    @DisplayName("combo citado por inteiro vence as partes; partes soltas viram as duas")
    void prefersLongest() {
        assertThat(nomes("quero corte e barba")).containsExactly("Corte e Barba");
        assertThat(nomes("corte, e depois barba")).containsExactly("Corte", "Barba");
    }

    @Test
    @DisplayName("nada citado, texto vazio ou catálogo vazio: lista vazia")
    void nothing() {
        assertThat(nomes("oi, tudo bem?")).isEmpty();
        assertThat(nomes(null)).isEmpty();
        assertThat(ServiceCapability.mentioned("barba", List.of())).isEmpty();
    }
}

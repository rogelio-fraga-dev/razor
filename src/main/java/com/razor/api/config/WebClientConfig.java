package com.razor.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WebClientConfig {

    private final RazorProperties properties;

    @Bean
    public WebClient webClient() {
        String apiKey = properties.getEvolution().getApiKey();
        return WebClient.builder()
                .baseUrl(properties.getEvolution().getBaseUrl())
                .defaultHeader("apikey", apiKey != null && !apiKey.isEmpty() ? apiKey : "barberbot")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // Sem @Bean ObjectMapper custom: um `new ObjectMapper()` pelado nao registra o JavaTimeModule
    // e faz o Spring Boot desistir do ObjectMapper auto-configurado, quebrando a serializacao de
    // java.time (LocalDate/LocalTime dos relatorios). Deixamos o Boot prover o ObjectMapper (com
    // JavaTimeModule e datas em ISO-8601), usado tanto pelo MVC quanto pelo WebhookController.
}

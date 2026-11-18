package com.razor.api.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class OpenAIConfig {
    
    private final RazorProperties properties;
    
    /** Modelo do dia a dia (padrao gpt-4o-mini). E o injetado por padrao em todo o sistema. */
    @Bean
    @org.springframework.context.annotation.Primary
    public OpenAiChatModel openAiChatModel() {
        return build(properties.getOpenai().getModel(), "gpt-4o-mini");
    }

    /**
     * Modelo mais forte (padrao gpt-4o) para quando o principal nao da conta: falha, resposta
     * vazia ou tag quebrada — e para ler imagem de agenda. Ver {@code ChatEscalation}.
     */
    @Bean
    public com.razor.api.service.ChatEscalation chatEscalation() {
        return new com.razor.api.service.ChatEscalation(
                build(properties.getOpenai().getEscalationModel(), "gpt-4o"));
    }

    private OpenAiChatModel build(String model, String fallback) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getOpenai().getApiKey())
                .modelName(model == null || model.isBlank() ? fallback : model)
                .temperature(properties.getOpenai().getTemperature())
                .timeout(Duration.ofSeconds(60)) // Aumentar timeout para imagens
                .build();
    }
}
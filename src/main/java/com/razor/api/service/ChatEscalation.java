package com.razor.api.service;

import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * O modelo de chat mais forte (padrao gpt-4o), separado do principal (gpt-4o-mini) para nao
 * disputar a injecao de {@link OpenAiChatModel}. O {@link OpenAIService} so recorre a ele quando o
 * principal falha, devolve vazio ou uma tag de agendamento quebrada — e para ler imagem de agenda.
 */
public class ChatEscalation {

    private final OpenAiChatModel model;

    public ChatEscalation(OpenAiChatModel model) {
        this.model = model;
    }

    public OpenAiChatModel model() {
        return model;
    }
}

package com.razor.api.service;

/**
 * A IA (OpenAI) nao respondeu — fora do ar, timeout, cota estourada, chave invalida.
 *
 * <p>Antes, qualquer falha virava a resposta "Opa, deu um pequeno erro no meu sistema" como se
 * fosse fala da IA: ia para o cliente, era gravada no historico como BOT (e realimentava o
 * modelo na proxima chamada) e ninguem da barbearia ficava sabendo. Agora a falha sobe como
 * excecao e quem chama decide a mensagem de contingencia e avisa o dono.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

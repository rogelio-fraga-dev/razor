package com.razor.api.service;

/**
 * Falha ao falar com um servico externo (ex.: Evolution API fora do ar). Mapeada para HTTP 502
 * no {@code ApiExceptionHandler} — sinaliza ao painel que o problema e de infraestrutura, nao do input.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message) {
        super(message);
    }
}

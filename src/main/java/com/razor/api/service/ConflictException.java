package com.razor.api.service;

/**
 * Operacao recusada porque deixaria o tenant num estado invalido ou conflitante (ex.: remover o
 * ultimo administrador). Mapeada para HTTP 409 pelo ApiExceptionHandler.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

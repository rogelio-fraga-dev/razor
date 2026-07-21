package com.razor.api.service;

/** Recurso nao encontrado no tenant atual. Mapeada para HTTP 404 na API do painel. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

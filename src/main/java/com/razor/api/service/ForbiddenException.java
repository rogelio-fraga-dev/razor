package com.razor.api.service;

/**
 * Pedido autenticado, mas sobre dado que nao e do usuario (ex.: barbeiro pedindo a comissao de
 * um colega). Mapeada para HTTP 403 na API do painel, com corpo {@code {"error": ...}}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

package com.razor.api.service;

/**
 * O pedido de agendamento era valido, mas o horario nao cabe mais (alguem ocupou entre a grade
 * aparecer e o visitante confirmar). Vira 409: a pagina recarrega a grade e pede outro horario.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}

package com.razor.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Limite de horarios futuros por cliente (V36). {@code null} = sem limite. Record proprio, como o
 * {@link GoalsPayload}: o PUT das configuracoes do bot troca o registro inteiro e zeraria o limite.
 */
public record BookingLimitPayload(@Positive @Max(50) Integer maxFutureBookingsPerCustomer) {
}

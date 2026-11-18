package com.razor.api.controller;

import com.razor.api.dto.BookingLimitPayload;
import com.razor.api.service.BarbershopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Limite de agendamentos por cliente (V36), no painel do dono. Endpoint proprio pelo mesmo motivo
 * das metas: o PUT das configuracoes do bot troca o registro inteiro.
 */
@RestController
@RequestMapping("/api/admin/settings/booking-limit")
@RequiredArgsConstructor
public class AdminBookingLimitController {

    private final BarbershopSettingsService barbershopSettingsService;

    @GetMapping
    public BookingLimitPayload get() {
        return new BookingLimitPayload(barbershopSettingsService.getMaxFutureBookingsPerCustomer());
    }

    /** Enviar {@code null} tira o limite. */
    @PutMapping
    public BookingLimitPayload update(@Valid @RequestBody BookingLimitPayload req) {
        return new BookingLimitPayload(
                barbershopSettingsService.setMaxFutureBookingsPerCustomer(req.maxFutureBookingsPerCustomer()));
    }
}

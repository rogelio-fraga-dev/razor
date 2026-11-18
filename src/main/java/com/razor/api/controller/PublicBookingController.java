package com.razor.api.controller;

import com.razor.api.dto.PublicBookingDtos;
import com.razor.api.service.PublicBookingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agendamento online — <b>publico</b>, sem autenticacao (pagina {@code /b/{slug}/agendar}).
 *
 * <p>Abaixo de {@code /api/site/} de proposito: herda a liberacao do SecurityConfig e o freio
 * por IP do RateLimitFilter (o POST tem um limite proprio, bem mais curto). 404 quando a
 * barbearia nao existe, nao publicou o site ou nao ligou o agendamento online.
 */
@RestController
@RequestMapping("/api/site/{slug}/booking")
@RequiredArgsConstructor
public class PublicBookingController {

    private final PublicBookingService bookingService;

    /** Servicos, barbeiros, dias abertos e a janela de datas. */
    @GetMapping
    public PublicBookingDtos.Options options(@PathVariable String slug) {
        return bookingService.options(slug);
    }

    /** Horarios livres ("HH:mm") — so os horarios, nunca quem ocupa os outros. */
    @GetMapping("/slots")
    public PublicBookingDtos.Slots slots(
            @PathVariable String slug,
            @RequestParam UUID serviceId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.slots(slug, serviceId, professionalId, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicBookingDtos.Confirmation book(@PathVariable String slug,
                                               @Valid @RequestBody PublicBookingDtos.Request request) {
        return bookingService.book(slug, request);
    }
}

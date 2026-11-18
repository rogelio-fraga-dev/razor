package com.razor.api.controller;

import com.razor.api.dto.CustomerTagRequests;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import com.razor.api.service.BroadcastService;
import com.razor.api.service.CustomerSegmentService;
import com.razor.api.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * "Enviar mensagem para este grupo" (V36): disparo do painel para os clientes de uma tag e/ou
 * status, pelo mesmo {@link BroadcastService} do disparo do WhatsApp do dono (mesma pausa entre
 * envios). Fica fora de {@code /customers/**} de proposito: falar com a base inteira e decisao do
 * dono, e a recepcao — que acessa clientes — cai no fecho {@code /api/admin/** = OWNER}.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/broadcasts")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private final CustomerSegmentService customerSegmentService;
    private final BroadcastService broadcastService;

    /** Quantos receberiam — a tela mostra antes de confirmar. */
    @GetMapping("/preview")
    public CustomerTagRequests.BroadcastResponse preview(@RequestParam(required = false) UUID tagId,
                                                         @RequestParam(required = false) CustomerStatus status) {
        return new CustomerTagRequests.BroadcastResponse(customerSegmentService.recipients(tagId, status).size());
    }

    /** Agenda o envio em segundo plano e responde na hora com quantos vao receber. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CustomerTagRequests.BroadcastResponse send(@Valid @RequestBody CustomerTagRequests.BroadcastRequest req) {
        // Sem tag nem status o recorte seria a base INTEIRA: um clique sem escolher o grupo nao
        // pode virar disparo para todo mundo. A base toda continua pelo WhatsApp do dono.
        if (req.tagId() == null && req.status() == null) {
            throw new IllegalArgumentException("Escolha um grupo");
        }
        List<Customer> recipients = customerSegmentService.recipients(req.tagId(), req.status());
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("Nenhum cliente neste grupo para receber a mensagem.");
        }
        UUID shop = TenantContext.getCurrent();
        log.info("[DISPARO] Painel: disparo para {} cliente(s) do tenant {} (tag={}, status={})",
                recipients.size(), shop, req.tagId(), req.status());
        broadcastService.deliverInBackground(shop, recipients, req.message().trim(), req.promo());
        return new CustomerTagRequests.BroadcastResponse(recipients.size());
    }
}

package com.razor.api.controller;

import com.razor.api.dto.AgendaBoardResponse;
import com.razor.api.dto.AgendaRequests;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AgendaPanelService;
import com.razor.api.service.AgendaPermissions;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Agenda interativa do painel (tenant vem do JWT). Leitura para todos os papeis; criar e marcar
 * sinal para dono e recepcao; remarcar, cancelar e comparecimento tambem para o barbeiro, mas so
 * nos proprios horarios — ver {@code SecurityConfig} e {@link AgendaPermissions}. As regras de
 * encaixe e os avisos ao cliente moram no {@link AgendaPanelService}.
 */
@RestController
@RequestMapping("/api/admin/agenda")
@RequiredArgsConstructor
public class AdminAgendaController {

    private final AgendaPanelService agendaPanelService;
    private final AgendaPermissions agendaPermissions;

    /** Grade de [from, to] (padrao: hoje). Ex.: {@code /api/admin/agenda?from=2026-11-16&to=2026-11-22}. */
    @GetMapping
    public AgendaBoardResponse board(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : AppointmentDates.today();
        LocalDate end = to != null ? to : start;
        return agendaPanelService.board(start, end);
    }

    @PostMapping("/appointments")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AgendaRequests.Create req) {
        var saved = agendaPanelService.create(req.customerName(), req.customerPhone(), req.serviceId(),
                req.professionalId(), req.date(), req.time(), AgendaRequests.shouldNotify(req.notifyCustomer()));
        return ResponseEntity.status(HttpStatus.CREATED).body(idOf(saved.getId()));
    }

    @PatchMapping("/appointments/{id}")
    public Map<String, Object> move(@AuthenticationPrincipal TenantUserPrincipal principal,
                                    @PathVariable Long id, @Valid @RequestBody AgendaRequests.Move req) {
        agendaPermissions.requireCanChange(principal, id, req.professionalId());
        var saved = agendaPanelService.move(id, req.date(), req.time(), req.professionalId(),
                AgendaRequests.shouldNotify(req.notifyCustomer()));
        return idOf(saved.getId());
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal TenantUserPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) AgendaRequests.Cancel req) {
        agendaPermissions.requireCanChange(principal, id, null);
        agendaPanelService.cancel(id, AgendaRequests.shouldNotify(req != null ? req.notifyCustomer() : null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments/{id}/attendance")
    public Map<String, Object> attendance(@AuthenticationPrincipal TenantUserPrincipal principal,
                                          @PathVariable Long id, @RequestBody AgendaRequests.Attendance req) {
        agendaPermissions.requireCanChange(principal, id, null);
        return idOf(agendaPanelService.setAttendance(id, req.status()).getId());
    }

    /** Sinal de cliente com faltas repetidas (V42) recebido. */
    @PostMapping("/appointments/{id}/deposit-paid")
    public Map<String, Object> depositPaid(@PathVariable Long id) {
        return idOf(agendaPanelService.confirmDeposit(id).getId());
    }

    private static Map<String, Object> idOf(Long id) {
        // Map.of nao aceita null.
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        return body;
    }
}

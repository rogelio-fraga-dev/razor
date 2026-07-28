package com.razor.api.controller;

import com.razor.api.dto.ComandaRequests;
import com.razor.api.dto.ComandaResponse;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.ComandaService;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Comandas do atendimento no painel web (tenant vem do JWT). Fechar a comanda exige caixa
 * aberto e lanca um unico movimento com o total — ver {@link ComandaService}.
 */
@RestController
@RequestMapping("/api/admin/comandas")
@RequiredArgsConstructor
public class AdminComandaController {

    private final ComandaService comandaService;
    private final ProfessionalRepository professionalRepository;

    /** Abertas (padrao) ou todas as de um dia com {@code ?date=}. */
    @GetMapping
    public List<ComandaResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Comanda> comandas = date != null ? comandaService.listByDate(date) : comandaService.listOpen();
        Map<UUID, String> names = professionalNames();
        return comandas.stream().map(c -> toResponse(c, names)).toList();
    }

    /** Comandas do dia inteiro (abertas, fechadas e canceladas). */
    @GetMapping("/today")
    public List<ComandaResponse> today() {
        Map<UUID, String> names = professionalNames();
        return comandaService.listByDate(AppointmentDates.today()).stream()
                .map(c -> toResponse(c, names)).toList();
    }

    @GetMapping("/{id}")
    public ComandaResponse get(@PathVariable UUID id) {
        return toResponse(comandaService.get(id), professionalNames());
    }

    @PostMapping
    public ResponseEntity<ComandaResponse> open(@Valid @RequestBody ComandaRequests.Open req) {
        Comanda comanda = comandaService.open(
                req.customerName(), req.customerPhone(), req.professionalId(), req.appointmentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda, professionalNames()));
    }

    @PostMapping("/{id}/services")
    public ComandaResponse addService(@PathVariable UUID id, @Valid @RequestBody ComandaRequests.AddItem req) {
        comandaService.addService(id, req.referenceId(), req.quantity());
        return toResponse(comandaService.get(id), professionalNames());
    }

    @PostMapping("/{id}/products")
    public ComandaResponse addProduct(@PathVariable UUID id, @Valid @RequestBody ComandaRequests.AddItem req) {
        comandaService.addProduct(id, req.referenceId(), req.quantity());
        return toResponse(comandaService.get(id), professionalNames());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ComandaResponse removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        comandaService.removeItem(id, itemId);
        return toResponse(comandaService.get(id), professionalNames());
    }

    @PostMapping("/{id}/close")
    public ComandaResponse close(@PathVariable UUID id, @Valid @RequestBody ComandaRequests.Close req) {
        return toResponse(comandaService.close(id, req.paymentMethod()), professionalNames());
    }

    @PostMapping("/{id}/cancel")
    public ComandaResponse cancel(@PathVariable UUID id) {
        return toResponse(comandaService.cancel(id), professionalNames());
    }

    private Map<UUID, String> professionalNames() {
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
    }

    private ComandaResponse toResponse(Comanda c, Map<UUID, String> names) {
        List<ComandaItem> items = comandaService.items(c.getId());
        String profName = c.getProfessionalId() == null ? null : names.get(c.getProfessionalId());
        return ComandaResponse.from(c, items, profName);
    }
}

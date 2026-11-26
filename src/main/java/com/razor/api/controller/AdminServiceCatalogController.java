package com.razor.api.controller;

import com.razor.api.dto.ServiceOfferingResponse;
import com.razor.api.dto.ServiceRequest;
import com.razor.api.model.ServiceOffering;
import com.razor.api.service.ServiceCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Config do catalogo de servicos no painel web (tenant vem do JWT). Substitui a configuracao
 * que hoje so existe no seed/codigo — o dono passa a gerir precos e duracoes pela web.
 */
@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
public class AdminServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public List<ServiceOfferingResponse> list() {
        return serviceCatalogService.listAll().stream().map(ServiceOfferingResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceRequest req) {
        ServiceOffering created = serviceCatalogService.create(req.name(), req.price(), req.durationMinutes(), req.commissionPercent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceOfferingResponse.from(created));
    }

    @PutMapping("/{id}")
    public ServiceOfferingResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest req) {
        boolean active = req.active() == null || req.active();
        ServiceOffering updated = serviceCatalogService.update(id, req.name(), req.price(), req.durationMinutes(), active, req.commissionPercent());
        return ServiceOfferingResponse.from(updated);
    }
}

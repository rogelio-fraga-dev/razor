package com.razor.api.controller;

import com.razor.api.dto.ProfessionalRequest;
import com.razor.api.dto.ProfessionalResponse;
import com.razor.api.model.Professional;
import com.razor.api.service.ProfessionalService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Config dos profissionais (barbeiros) e suas comissoes no painel web (tenant vem do JWT).
 */
@RestController
@RequestMapping("/api/admin/professionals")
@RequiredArgsConstructor
public class AdminProfessionalController {

    private final ProfessionalService professionalService;

    @GetMapping
    public List<ProfessionalResponse> list() {
        return professionalService.listAll().stream().map(ProfessionalResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProfessionalResponse> create(@Valid @RequestBody ProfessionalRequest req) {
        Professional created = professionalService.create(req.name(), req.phone());
        BigDecimal commission = req.commissionPercent();
        if (commission != null && commission.signum() != 0) {
            created = professionalService.updateCommission(created.getId(), commission);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfessionalResponse.from(created));
    }

    @PutMapping("/{id}")
    public ProfessionalResponse update(@PathVariable UUID id, @Valid @RequestBody ProfessionalRequest req) {
        boolean active = req.active() == null || req.active();
        BigDecimal commission = req.commissionPercent() == null ? BigDecimal.ZERO : req.commissionPercent();
        Professional updated = professionalService.update(id, req.name(), req.phone(), active, commission);
        return ProfessionalResponse.from(updated);
    }
}

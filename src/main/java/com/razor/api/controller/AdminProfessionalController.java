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
        // Valida o perfil antes de criar: foto recusada nao pode deixar um cadastro pela metade.
        professionalService.checkProfile(req.profile());
        Professional created = professionalService.create(req.name(), req.phone());
        BigDecimal commission = req.commissionPercent();
        if (commission != null && commission.signum() != 0) {
            created = professionalService.updateCommission(created.getId(), commission);
        }
        if (!req.profile().isEmpty()) {
            created = professionalService.updateProfile(created.getId(), req.profile());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfessionalResponse.from(created));
    }

    @PutMapping("/{id}")
    public ProfessionalResponse update(@PathVariable UUID id, @Valid @RequestBody ProfessionalRequest req) {
        boolean active = req.active() == null || req.active();
        BigDecimal commission = req.commissionPercent() == null ? BigDecimal.ZERO : req.commissionPercent();
        // Produto nulo continua nulo de proposito: significa "usa o percentual dos servicos",
        // que e diferente de zero ("nao paga comissao de produto").
        // Perfil (V37) primeiro: e onde mora a validacao que mais recusa (foto, link). Se ela
        // falhar, nada foi gravado — nem o nome. Campo nulo nao mexe: cliente antigo nao apaga a
        // foto de ninguem.
        if (!req.profile().isEmpty()) {
            professionalService.updateProfile(id, req.profile());
        }
        Professional updated = professionalService.update(
                id, req.name(), req.phone(), active, commission, req.productCommissionPercent());
        return ProfessionalResponse.from(updated);
    }
}

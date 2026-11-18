package com.razor.api.controller;

import com.razor.api.dto.ScheduleBlockPayload;
import com.razor.api.model.Professional;
import com.razor.api.model.ScheduleBlock;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.ScheduleBlockService;
import com.razor.api.tenant.TenantContext;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Folgas e bloqueios de agenda no painel (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/schedule-blocks")
@RequiredArgsConstructor
public class AdminScheduleBlockController {

    private final ScheduleBlockService scheduleBlockService;
    private final ProfessionalRepository professionalRepository;

    @GetMapping
    public List<ScheduleBlockPayload> list() {
        Map<UUID, String> nomes = professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
        return scheduleBlockService.listAll().stream()
                .map(b -> ScheduleBlockPayload.from(b,
                        b.getProfessionalId() == null ? null : nomes.get(b.getProfessionalId())))
                .sorted(Comparator.comparing(ScheduleBlockPayload::date,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<ScheduleBlockPayload> create(@RequestBody ScheduleBlockPayload req) {
        ScheduleBlock created = scheduleBlockService.create(
                req.professionalId(), req.date(), req.weekday(), req.startTime(), req.endTime(), req.reason());
        // Busca escopada no tenant (o servico ja recusou profissional de outra barbearia; o filtro
        // aqui garante que o nome nunca venha de outro tenant).
        String nome = created.getProfessionalId() == null ? null
                : professionalRepository.findById(created.getProfessionalId())
                        .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                        .map(Professional::getName).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleBlockPayload.from(created, nome));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        scheduleBlockService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

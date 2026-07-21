package com.razor.api.controller;

import com.razor.api.dto.AdminContactRequest;
import com.razor.api.dto.AdminContactResponse;
import com.razor.api.service.TenantAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Gestao dos telefones administrativos (admins do WhatsApp) da barbearia no painel web
 * (tenant vem do JWT).
 */
@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminBarbershopAdminController {

    private final TenantAdminService tenantAdminService;

    @GetMapping
    public List<AdminContactResponse> list() {
        return tenantAdminService.listAdmins().stream().map(AdminContactResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<AdminContactResponse> add(@Valid @RequestBody AdminContactRequest req) {
        AdminContactResponse created = AdminContactResponse.from(tenantAdminService.add(req.phone(), req.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        tenantAdminService.remove(id);
        return ResponseEntity.noContent().build();
    }
}

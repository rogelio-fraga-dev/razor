package com.razor.api.controller;

import com.razor.api.dto.BarbershopResponse;
import com.razor.api.dto.BarbershopUpdateRequest;
import com.razor.api.service.BarbershopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Perfil da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/barbershop")
@RequiredArgsConstructor
public class AdminBarbershopController {

    private final BarbershopService barbershopService;

    @GetMapping
    public BarbershopResponse get() {
        return BarbershopResponse.from(barbershopService.current());
    }

    @PutMapping
    public BarbershopResponse update(@Valid @RequestBody BarbershopUpdateRequest req) {
        return BarbershopResponse.from(barbershopService.updateProfile(req.name()));
    }
}

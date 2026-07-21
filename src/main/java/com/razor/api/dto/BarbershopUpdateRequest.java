package com.razor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edicao do perfil da barbearia (o slug e imutavel). */
public record BarbershopUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 120) String whatsappInstance) {
}

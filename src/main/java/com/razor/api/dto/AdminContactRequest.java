package com.razor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro de um telefone administrativo (admin do WhatsApp). */
public record AdminContactRequest(
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 255) String name) {
}

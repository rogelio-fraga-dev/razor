package com.razor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload de provisionamento de uma nova barbearia (tenant) no onboarding self-service.
 *
 * @param name             nome da barbearia (obrigatorio)
 * @param slug             identificador para URL, unico (minusculas, numeros e hifen)
 * @param whatsappInstance nome da instancia da Evolution do tenant (opcional; recomendado
 *                         para o roteamento de saida — sem ele o tenant usa a instancia do config)
 * @param professionalName nome do barbeiro padrao criado no provisionamento (opcional)
 * @param adminPhones      telefones administrativos do tenant (ao menos um)
 * @param ownerEmail       email de login do dono (usuario OWNER do painel web)
 * @param ownerPassword    senha inicial do dono (min. 8 caracteres)
 */
public record OnboardingRequest(
        @NotBlank String name,

        @NotBlank
        // Formato de rotulo DNS (ate 63, sem hifen nas pontas): o slug ja vai na URL do site e
        // deve poder virar subdominio no futuro sem renomear barbearia.
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                message = "slug deve conter apenas letras minusculas, numeros e hifen (sem hifen no inicio/fim, ate 63)")
        @Size(max = 63)
        String slug,

        @Size(max = 120) String whatsappInstance,

        @Size(max = 255) String professionalName,

        @NotEmpty List<@NotBlank String> adminPhones,

        @NotBlank @Email @Size(max = 255) String ownerEmail,

        @NotBlank @Size(min = 8, max = 100) String ownerPassword) {
}

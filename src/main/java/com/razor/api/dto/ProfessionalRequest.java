package com.razor.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload de criacao/atualizacao de um profissional. No POST, {@code active} e ignorado (nasce
 * ativo) e {@code commissionPercent} nulo vira 0; no PUT, ambos sao aplicados.
 */
public record ProfessionalRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        Boolean active,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent) {
}

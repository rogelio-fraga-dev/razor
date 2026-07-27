package com.razor.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload de criacao/atualizacao de um profissional. No POST, {@code active} e ignorado (nasce
 * ativo) e {@code commissionPercent} nulo vira 0; no PUT, ambos sao aplicados.
 *
 * <p>{@code productCommissionPercent} nulo significa "usa o mesmo percentual dos servicos" (V23) —
 * nao e o mesmo que zero, que seria "nao paga comissao de produto".
 */
public record ProfessionalRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        Boolean active,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,
        @DecimalMin("0") @DecimalMax("100") BigDecimal productCommissionPercent) {
}

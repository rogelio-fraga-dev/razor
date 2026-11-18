package com.razor.api.dto;

import com.razor.api.service.ProfessionalService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload de criacao/atualizacao de um profissional. No POST, {@code active} e ignorado (nasce
 * ativo) e {@code commissionPercent} nulo vira 0; no PUT, ambos sao aplicados.
 *
 * <p>{@code productCommissionPercent} nulo significa "usa o mesmo percentual dos servicos" (V23) —
 * nao e o mesmo que zero, que seria "nao paga comissao de produto".
 *
 * <p><b>Perfil (V37):</b> nos campos de perfil, nulo = "nao mexe" e vazio = "limpa". A validacao
 * de verdade (data URI da foto, URL do Instagram, servicos do tenant) fica no servico; os
 * {@code @Size} aqui so cortam payload absurdo antes de chegar la.
 */
public record ProfessionalRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        Boolean active,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,
        @DecimalMin("0") @DecimalMax("100") BigDecimal productCommissionPercent,
        /** Data URI {@code data:image/jpeg|png|webp;base64,...}; {@code ""} remove a foto. */
        @Size(max = 100_000) String photoUrl,
        @Size(max = 255) String nickname,
        @Size(max = 2000) String bio,
        @Size(max = 50) List<String> specialties,
        @Size(max = 500) String instagramUrl,
        /** Servicos que atende; lista vazia = todos. */
        @Size(max = 500) List<UUID> serviceIds) {

    /** Compatibilidade: payload sem perfil (so cadastro e comissao). */
    public ProfessionalRequest(String name, String phone, Boolean active,
                               BigDecimal commissionPercent, BigDecimal productCommissionPercent) {
        this(name, phone, active, commissionPercent, productCommissionPercent,
                null, null, null, null, null, null);
    }

    public ProfessionalService.ProfileUpdate profile() {
        return new ProfessionalService.ProfileUpdate(
                photoUrl, nickname, bio, specialties, instagramUrl, serviceIds);
    }
}

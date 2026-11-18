package com.razor.api.dto;

import com.razor.api.model.Professional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Representacao de um profissional (barbeiro) para a API do painel.
 *
 * <p>{@code photoUrl} e um data URI (V37) pronto para {@code <img src>}; {@code displayName} e
 * o apelido quando existe, senao o nome — o mesmo que o bot usa com o cliente.
 */
public record ProfessionalResponse(
        UUID id, String name, String phone, boolean active, BigDecimal commissionPercent,
        /** {@code null} = usa o percentual dos servicos (V23). */
        BigDecimal productCommissionPercent,
        String photoUrl,
        String nickname,
        String displayName,
        String bio,
        List<String> specialties,
        String instagramUrl,
        /** Vazio = atende todos os servicos. */
        List<UUID> serviceIds) {

    public static ProfessionalResponse from(Professional p) {
        return new ProfessionalResponse(p.getId(), p.getName(), p.getPhone(), p.isActive(),
                p.getCommissionPercent(), p.getProductCommissionPercent(),
                p.getPhotoData(), p.getNickname(), p.displayName(), p.getBio(), p.specialtyList(),
                p.getInstagramUrl(),
                p.getServiceIds() == null ? List.of() : p.getServiceIds().stream().sorted().toList());
    }
}

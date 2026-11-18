package com.razor.api.service;

import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gestao dos profissionais (barbeiros) da barbearia — sempre escopado no tenant atual.
 * Base da agenda multiprofissional (Fase 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final ProfessionalRepository professionalRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    /**
     * Alteracao do perfil (V37). <b>Campo nulo = nao mexe</b>; para limpar, mande vazio
     * ({@code ""} ou lista vazia). Assim um cliente antigo que so conhece nome/comissao nao apaga
     * a foto de ninguem sem querer. {@code serviceIds} vazio = "atende todos".
     */
    public record ProfileUpdate(String photo, String nickname, String bio, List<String> specialties,
                                String instagramUrl, List<UUID> serviceIds) {

        public boolean isEmpty() {
            return photo == null && nickname == null && bio == null && specialties == null
                    && instagramUrl == null && serviceIds == null;
        }
    }

    /** Profissionais ativos do tenant atual. */
    public List<Professional> listActive() {
        return professionalRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
    }

    /**
     * Profissionais ativos que atendem o servico pedido (V37) — o bot so oferece ao cliente quem
     * faz o que ele pediu. Servico nulo ou fora do catalogo nao filtra (ver {@link ServiceCapability}).
     */
    public List<Professional> listActiveFor(String serviceName) {
        List<Professional> ativos = listActive();
        if (serviceName == null || serviceName.isBlank()
                || ativos.stream().allMatch(Professional::servesAllServices)) {
            return ativos;
        }
        return ServiceCapability.qualified(ativos, serviceName,
                serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent()));
    }

    /** Todos os profissionais (ativos e inativos) do tenant atual. */
    public List<Professional> listAll() {
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    public Professional create(String name, String phone) {
        Professional created = professionalRepository.save(Professional.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(name)
                .phone(phone)
                .active(true)
                .build());
        log.info("[PROFISSIONAL] '{}' criado para o tenant {}", name, TenantContext.getCurrent());
        return created;
    }

    private static final BigDecimal CEM = new BigDecimal("100");

    /**
     * Define o percentual de comissao (0–100) de um profissional do tenant atual.
     * Rejeita valores fora do intervalo e profissional de outro tenant (isolamento).
     */
    public Professional updateCommission(UUID professionalId, BigDecimal percent) {
        validateCommission(percent);
        Professional professional = findOwned(professionalId);
        professional.setCommissionPercent(percent);
        Professional saved = professionalRepository.save(professional);
        log.info("[PROFISSIONAL] Comissao de '{}' definida em {}%", professional.getName(), percent);
        return saved;
    }

    /**
     * Atualiza nome, telefone, status e comissoes de um profissional do tenant atual.
     *
     * <p>{@code productCommissionPercent} nulo significa "usa o mesmo percentual dos servicos" —
     * e o que vale para quem nunca configurou um percentual proprio de produto (V23).
     */
    public Professional update(UUID id, String name, String phone, boolean active,
                               BigDecimal commissionPercent, BigDecimal productCommissionPercent) {
        validateCommission(commissionPercent);
        if (productCommissionPercent != null) {
            validateCommission(productCommissionPercent);
        }
        Professional professional = findOwned(id);
        professional.setName(name);
        professional.setPhone(phone);
        professional.setActive(active);
        professional.setCommissionPercent(commissionPercent);
        professional.setProductCommissionPercent(productCommissionPercent);
        return professionalRepository.save(professional);
    }

    /**
     * Aplica o perfil (foto, apelido, bio, especialidades, Instagram, servicos que atende) a um
     * profissional do tenant atual. Tudo e validado antes de qualquer escrita — ver
     * {@link ProfessionalProfileRules}. Servico de outro tenant e recusado (isolamento).
     */
    public Professional updateProfile(UUID id, ProfileUpdate p) {
        if (p == null || p.isEmpty()) return findOwned(id);

        // Valida tudo primeiro: um campo invalido nao pode deixar o perfil meio salvo.
        Validated v = validate(p);

        Professional professional = findOwned(id);
        if (p.photo() != null) professional.setPhotoData(v.photo());
        if (p.nickname() != null) professional.setNickname(v.nickname());
        if (p.bio() != null) professional.setBio(v.bio());
        if (p.specialties() != null) professional.setSpecialties(v.specialties());
        if (p.instagramUrl() != null) professional.setInstagramUrl(v.instagram());
        if (v.serviceIds() != null) {
            // Muta a colecao gerenciada em vez de trocar a referencia (orphan-safe no Hibernate).
            if (professional.getServiceIds() == null) professional.setServiceIds(new HashSet<>());
            professional.getServiceIds().clear();
            professional.getServiceIds().addAll(v.serviceIds());
        }
        Professional saved = professionalRepository.save(professional);
        log.info("[PROFISSIONAL] Perfil de '{}' atualizado ({} servicos restritos)",
                professional.getName(), saved.getServiceIds() == null ? 0 : saved.getServiceIds().size());
        return saved;
    }

    /**
     * Valida o perfil sem gravar nada — para o cadastro checar <b>antes</b> de criar o barbeiro
     * (senao uma foto recusada deixaria um cadastro pela metade, e o dono criaria outro).
     *
     * @throws IllegalArgumentException no primeiro campo invalido
     */
    public void checkProfile(ProfileUpdate p) {
        if (p != null && !p.isEmpty()) validate(p);
    }

    private record Validated(String photo, String nickname, String bio, String specialties,
                             String instagram, Set<UUID> serviceIds) {
    }

    private Validated validate(ProfileUpdate p) {
        return new Validated(
                p.photo() == null ? null : ProfessionalProfileRules.validatePhoto(p.photo()),
                p.nickname() == null ? null
                        : ProfessionalProfileRules.optionalText(p.nickname(), ProfessionalProfileRules.MAX_NICKNAME, "Apelido"),
                p.bio() == null ? null
                        : ProfessionalProfileRules.optionalText(p.bio(), ProfessionalProfileRules.MAX_BIO, "Bio"),
                ProfessionalProfileRules.normalizeSpecialties(p.specialties()),
                p.instagramUrl() == null ? null : ProfessionalProfileRules.validateInstagram(p.instagramUrl()),
                p.serviceIds() == null ? null : validateServiceIds(p.serviceIds()));
    }

    /** So servicos do proprio tenant. Um id estranho e erro de cliente, nao ruido a ignorar. */
    private Set<UUID> validateServiceIds(List<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) return wanted;
        Set<UUID> owned = serviceOfferingRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .map(ServiceOffering::getId)
                .collect(Collectors.toSet());
        if (!owned.containsAll(wanted)) {
            throw new IllegalArgumentException("Serviço inválido na lista de serviços do barbeiro.");
        }
        return wanted;
    }

    private void validateCommission(BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(CEM) > 0) {
            throw new IllegalArgumentException("Comissao deve estar entre 0 e 100.");
        }
    }

    private Professional findOwned(UUID id) {
        return professionalRepository.findById(id)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));
    }
}

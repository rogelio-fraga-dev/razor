package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profissional (barbeiro) de uma barbearia — base da agenda multiprofissional (Fase 1).
 */
@Entity
@Table(name = "professionals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Percentual de comissao do barbeiro sobre o valor dos servicos (0–100). */
    @Builder.Default
    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    /**
     * Percentual de comissao sobre <b>produtos</b> vendidos (V23). {@code null} = usa o mesmo
     * percentual dos servicos — o comportamento anterior, preservado para nao alterar a comissao
     * de ninguem sem o dono pedir. Use {@link #effectiveProductCommissionPercent()} para ler.
     */
    @Column(name = "product_commission_percent", precision = 5, scale = 2)
    private BigDecimal productCommissionPercent;

    /** O percentual que vale para produto: o proprio, ou o de servico quando nao definido. */
    public BigDecimal effectiveProductCommissionPercent() {
        if (productCommissionPercent != null) return productCommissionPercent;
        return commissionPercent != null ? commissionPercent : BigDecimal.ZERO;
    }

    // ---------- Perfil (V37) ----------

    /**
     * Foto como data URI ({@code data:image/jpeg;base64,...}), ja recortada e reduzida no
     * navegador. Validada em {@code ProfessionalProfileValidator}: so imagem, so base64, ate
     * ~60KB. Fica fora do {@code toString} — 40KB de base64 num log nao ajudam ninguem.
     */
    @lombok.ToString.Exclude
    @Column(name = "photo_data", columnDefinition = "text")
    private String photoData;

    /** Como o bot chama o barbeiro ("Diego" em vez de "Diego Aparecido Santos"). */
    @Column(length = 60)
    private String nickname;

    /** Bio curta para a pagina da equipe no site. */
    @Column(length = 500)
    private String bio;

    /** Especialidades, uma por linha — ver {@link #specialtyList()}. */
    @Column(columnDefinition = "text")
    private String specialties;

    @Column(name = "instagram_url")
    private String instagramUrl;

    /**
     * Servicos que este barbeiro atende. <b>Vazio = todos</b> — o estado de quem ja existia antes
     * da V37, para que ninguem deixe de receber agendamento por causa dela.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "professional_services", joinColumns = @JoinColumn(name = "professional_id"))
    @Column(name = "service_id")
    private Set<UUID> serviceIds = new HashSet<>();

    /** Nome para falar com o cliente: o apelido, quando definido; senao o nome. */
    public String displayName() {
        return nickname != null && !nickname.isBlank() ? nickname.trim() : name;
    }

    public List<String> specialtyList() {
        if (specialties == null || specialties.isBlank()) return List.of();
        return specialties.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Atende todos os servicos (nenhuma restricao configurada)? */
    public boolean servesAllServices() {
        return serviceIds == null || serviceIds.isEmpty();
    }

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

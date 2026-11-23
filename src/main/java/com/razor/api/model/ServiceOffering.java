package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Servico oferecido por uma barbearia (corte, barba, combo...) com preco e duracao.
 * Mapeia a tabela {@code services}; nome da classe evita colisao com {@code @Service}
 * do Spring. Base do catalogo de servicos (Fase 1).
 */
@Entity
@Table(name = "services")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 30;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Percentual de comissao do barbeiro NESTE servico (V27). {@code null} = usa o percentual
     * geral do profissional (V23), preservando o comportamento de quem nunca configurou por
     * servico. {@code 0} e diferente de nulo: significa "este servico nao paga comissao".
     */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * Lembrete de retorno (V47): dias depois do atendimento deste servico para lembrar o cliente
     * de voltar (ex.: corte = 21). {@code null} = sem lembrete para este servico (padrao).
     * Configurado na aba Automacoes.
     */
    @Column(name = "return_reminder_days")
    private Integer returnReminderDays;

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

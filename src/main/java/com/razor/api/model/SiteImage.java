package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Foto do site white-label enviada pelo dono (V45). Mapeia {@code site_image}.
 *
 * <p>Sem {@code @Data} de proposito: o {@code toString}/{@code equals} gerados passariam pelos
 * bytes da imagem (ate 2MB) em cada log ou comparacao.
 */
@Entity
@Table(name = "site_image")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteImage {

    /** Gerado pela aplicacao (aleatorio): e o que vai na URL publica. */
    @Id
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    /** Tipo detectado pelos bytes no upload — nunca o declarado pelo navegador. */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    /**
     * Os bytes da foto quando ela mora no banco ({@code storageBackend = DB}). Nulo quando mora
     * no S3 (V50) — ai a linha e so o registro e quem guarda o arquivo e o {@code ImageStorage}.
     */
    @Column(name = "bytes", columnDefinition = "bytea")
    private byte[] bytes;

    /** Onde os bytes estao (V50): {@code DB} (padrao) ou {@code S3}. */
    @Builder.Default
    @Column(name = "storage_backend", nullable = false, length = 10)
    private String storageBackend = "DB";

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

package com.razor.api.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vinculo cliente x tag (V36), {@code customer_tag_links}. Entidade propria em vez de
 * {@code @ManyToMany} no {@link Customer}: o cliente e um {@code @Data} usado em todo canto
 * (bot, scheduler, relatorios), e pendurar uma colecao lazy nele faria cada um desses caminhos
 * arriscar {@code LazyInitializationException} ou carregar tags que nunca usa.
 */
@Entity
@Table(name = "customer_tag_links")
@IdClass(CustomerTagLink.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTagLink {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Id
    @Column(name = "tag_id")
    private UUID tagId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public CustomerTagLink(UUID customerId, UUID tagId) {
        this(customerId, tagId, null);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Chave composta (cliente, tag) — a mesma do PK da tabela. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID customerId;
        private UUID tagId;
    }
}

package com.razor.api.service;

import com.razor.api.model.Product;
import com.razor.api.repository.ProductRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Catalogo de produtos vendidos no balcao (V20) — sempre escopado no tenant atual.
 * Base da venda de balcao (entra no caixa e gera comissao) e da comanda por atendimento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final ProductRepository productRepository;

    /** Produtos ativos do tenant atual — o que aparece na hora de vender. */
    public List<Product> listActive() {
        return productRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
    }

    /** Todos os produtos (ativos e inativos) do tenant atual — a tela de gestao. */
    public List<Product> listAll() {
        return productRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    public Product create(String name, BigDecimal price) {
        String cleanName = requireName(name);
        BigDecimal cleanPrice = requirePositivePrice(price);
        requireNameAvailable(cleanName, null);

        Product created = productRepository.save(Product.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(cleanName)
                .price(cleanPrice)
                .active(true)
                .build());
        log.info("[PRODUTOS] Produto '{}' criado para o tenant {}", cleanName, TenantContext.getCurrent());
        return created;
    }

    /** Atualiza um produto do tenant atual (isolamento: rejeita produto de outro tenant). */
    public Product update(UUID id, String name, BigDecimal price, boolean active) {
        String cleanName = requireName(name);
        BigDecimal cleanPrice = requirePositivePrice(price);

        Product product = productRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado."));
        requireNameAvailable(cleanName, id);

        product.setName(cleanName);
        product.setPrice(cleanPrice);
        product.setActive(active);
        return productRepository.save(product);
    }

    /**
     * Desativa em vez de apagar: produto ja vendido precisa continuar existindo para os
     * relatorios e comandas antigas nao perderem a referencia.
     */
    public Product deactivate(UUID id) {
        Product product = productRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado."));
        product.setActive(false);
        productRepository.save(product);
        log.info("[PRODUTOS] Produto '{}' desativado no tenant {}", product.getName(), TenantContext.getCurrent());
        return product;
    }

    private String requireName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Informe o nome do produto.");
        return clean;
    }

    private BigDecimal requirePositivePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("O preco do produto deve ser maior que zero.");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    /** O banco tem indice unico por (tenant, nome); aqui a mensagem sai legivel em vez de 500. */
    private void requireNameAvailable(String name, UUID ignoreId) {
        productRepository.findByBarbershopIdAndNameIgnoreCase(TenantContext.getCurrent(), name)
                .filter(existing -> !existing.getId().equals(ignoreId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Ja existe um produto chamado \"" + name + "\".");
                });
    }
}

package com.razor.api.controller;

import com.razor.api.dto.ProductRequest;
import com.razor.api.dto.ProductResponse;
import com.razor.api.model.Product;
import com.razor.api.service.ProductCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Catalogo de produtos do balcao no painel web (tenant vem do JWT). Pre-requisito da venda de
 * balcao e da comanda por atendimento.
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductCatalogService productCatalogService;

    /** Todos os produtos (ativos e inativos) — a tela de gestao mostra os dois. */
    @GetMapping
    public List<ProductResponse> list() {
        return productCatalogService.listAll().stream().map(ProductResponse::from).toList();
    }

    /** So os ativos — o que pode ser vendido agora. */
    @GetMapping("/active")
    public List<ProductResponse> listActive() {
        return productCatalogService.listActive().stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        Product created = productCatalogService.create(req.name(), req.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        boolean active = req.active() == null || req.active();
        return ProductResponse.from(productCatalogService.update(id, req.name(), req.price(), active));
    }

    /** Desativa (nao apaga): produto ja vendido precisa continuar existindo nos relatorios. */
    @DeleteMapping("/{id}")
    public ProductResponse deactivate(@PathVariable UUID id) {
        return ProductResponse.from(productCatalogService.deactivate(id));
    }
}

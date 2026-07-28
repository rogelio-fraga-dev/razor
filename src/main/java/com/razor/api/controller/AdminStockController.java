package com.razor.api.controller;

import com.razor.api.dto.ProductResponse;
import com.razor.api.dto.StockMovementResponse;
import com.razor.api.dto.StockRequests;
import com.razor.api.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Estoque dos produtos no painel web (tenant vem do JWT). A baixa da venda e automatica —
 * ver {@link StockService}; aqui ficam reposicao, perda, ajuste de contagem e o extrato.
 */
@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    /** Produtos controlados que ja bateram no nivel de reposicao. */
    @GetMapping("/low")
    public List<ProductResponse> lowStock() {
        return stockService.lowStock().stream().map(ProductResponse::from).toList();
    }

    /** Extrato de um produto: o que explica o saldo atual. */
    @GetMapping("/{productId}/movements")
    public List<StockMovementResponse> movements(@PathVariable UUID productId) {
        return stockService.history(productId).stream().map(StockMovementResponse::from).toList();
    }

    /** Reposicao: chegou mercadoria. Liga o controle de estoque se estiver desligado. */
    @PostMapping("/{productId}/entrada")
    public ProductResponse addStock(@PathVariable UUID productId,
                                    @Valid @RequestBody StockRequests.Movement req) {
        return ProductResponse.from(stockService.addStock(productId, req.quantity(), req.note()));
    }

    /** Perda, quebra ou uso interno: sai do estoque sem passar pelo caixa. */
    @PostMapping("/{productId}/saida")
    public ProductResponse removeStock(@PathVariable UUID productId,
                                       @Valid @RequestBody StockRequests.Movement req) {
        return ProductResponse.from(stockService.removeStock(productId, req.quantity(), req.note()));
    }

    /** Contagem fisica: o saldo passa a ser exatamente o informado. */
    @PostMapping("/{productId}/ajuste")
    public ProductResponse adjust(@PathVariable UUID productId,
                                  @Valid @RequestBody StockRequests.Adjustment req) {
        return ProductResponse.from(stockService.adjustTo(productId, req.countedQuantity(), req.note()));
    }

    /** Liga/desliga o controle e define o nivel de reposicao. */
    @PutMapping("/{productId}/config")
    public ProductResponse configure(@PathVariable UUID productId,
                                     @Valid @RequestBody StockRequests.Config req) {
        int minStock = req.minStock() == null ? 0 : req.minStock();
        return ProductResponse.from(
                stockService.configure(productId, Boolean.TRUE.equals(req.trackStock()), minStock));
    }
}

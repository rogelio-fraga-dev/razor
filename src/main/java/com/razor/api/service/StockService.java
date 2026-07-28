package com.razor.api.service;

import com.razor.api.model.Product;
import com.razor.api.model.StockMovement;
import com.razor.api.model.StockMovementType;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.StockMovementRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estoque dos produtos do balcao (V24). Reposicao, baixa na venda, perda e ajuste de contagem.
 *
 * <p><b>Controle e opt-in por produto.</b> Com {@code trackStock} desligado nada aqui bloqueia
 * nem movimenta nada — a barbearia que nao conta estoque vende como antes. Ligado, a venda so
 * passa se houver saldo: e o que "controlar estoque" significa, e o dono pediu explicitamente
 * ao ligar a chave.
 *
 * <p>Toda mudanca de saldo grava um {@link StockMovement}. Sem historico, "meu estoque esta
 * errado" nao tem como ser investigado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    /** Extrato de um produto do tenant atual. */
    public List<StockMovement> history(UUID productId) {
        requireOwned(productId);
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    /** Produtos controlados que ja bateram no nivel de reposicao. */
    public List<Product> lowStock() {
        return productRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent()).stream()
                .filter(Product::isLowStock)
                .toList();
    }

    /**
     * Confere se da para vender {@code quantity} unidades. Produto sem controle passa sempre.
     * Chamado antes de cobrar, para a venda falhar inteira em vez de deixar saldo negativo.
     */
    public void requireAvailable(Product product, int quantity) {
        if (!product.isTrackStock()) return;
        if (product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException(
                    "Estoque insuficiente de \"" + product.getName() + "\": restam "
                            + product.getStockQuantity() + " unidade(s).");
        }
    }

    /** Reposicao: chegou mercadoria. Liga o controle se ainda estiver desligado. */
    @Transactional
    public Product addStock(UUID productId, int quantity, String note) {
        requirePositive(quantity);
        Product product = requireOwned(productId);
        // Repor e a acao que declara "eu conto este produto" — ligar sozinho evita o estado
        // confuso de ter saldo cadastrado com o controle desligado.
        product.setTrackStock(true);
        return applyDelta(product, StockMovementType.ENTRADA, quantity, quantity, null, note);
    }

    /** Perda, quebra ou uso interno: sai do estoque sem passar pelo caixa. */
    @Transactional
    public Product removeStock(UUID productId, int quantity, String note) {
        requirePositive(quantity);
        Product product = requireOwned(productId);
        requireAvailable(product, quantity);
        return applyDelta(product, StockMovementType.SAIDA, quantity, -quantity, null, note);
    }

    /**
     * Baixa da venda. Chamado pela venda de balcao e pelo fechamento de comanda, para os dois
     * caminhos passarem exatamente pela mesma regra.
     */
    @Transactional
    public void registerSale(Product product, int quantity, UUID productSaleId) {
        if (!product.isTrackStock()) return;
        requireAvailable(product, quantity);
        applyDelta(product, StockMovementType.SAIDA, quantity, -quantity, productSaleId, "Venda");
    }

    /** Correcao apos contagem fisica: o saldo passa a ser exatamente {@code countedQuantity}. */
    @Transactional
    public Product adjustTo(UUID productId, int countedQuantity, String note) {
        if (countedQuantity < 0) {
            throw new IllegalArgumentException("A contagem nao pode ser negativa.");
        }
        Product product = requireOwned(productId);
        product.setTrackStock(true);
        int delta = countedQuantity - product.getStockQuantity();
        if (delta == 0) return product;
        return applyDelta(product, StockMovementType.AJUSTE, Math.abs(delta), delta, null, note);
    }

    /** Liga/desliga o controle e define o nivel de reposicao. */
    @Transactional
    public Product configure(UUID productId, boolean trackStock, int minStock) {
        if (minStock < 0) {
            throw new IllegalArgumentException("O estoque minimo nao pode ser negativo.");
        }
        Product product = requireOwned(productId);
        product.setTrackStock(trackStock);
        product.setMinStock(minStock);
        return productRepository.save(product);
    }

    // ---------- apoio ----------

    private Product applyDelta(Product product, StockMovementType type, int quantity, int delta,
                               UUID productSaleId, String note) {
        int resulting = product.getStockQuantity() + delta;
        product.setStockQuantity(resulting);
        Product saved = productRepository.save(product);

        stockMovementRepository.save(StockMovement.builder()
                .barbershopId(saved.getBarbershopId())
                .productId(saved.getId())
                .type(type)
                .quantity(quantity)
                .resultingStock(resulting)
                .productSaleId(productSaleId)
                .note(note == null || note.isBlank() ? null : note.trim())
                .build());

        if (saved.isLowStock()) {
            log.info("[ESTOQUE] '{}' no nivel de reposicao: {} restante(s), minimo {}",
                    saved.getName(), resulting, saved.getMinStock());
        }
        return saved;
    }

    private Product requireOwned(UUID productId) {
        return productRepository.findByIdAndBarbershopId(productId, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado."));
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
    }
}

package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Product;
import com.razor.api.model.StockMovement;
import com.razor.api.model.StockMovementType;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.StockMovementRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Testes do estoque (V24): opt-in, baixa na venda, alerta de mínimo e extrato. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockMovementRepository stockMovementRepository;

    @InjectMocks private StockService service;

    private final UUID productId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product product(boolean trackStock, int stock, int min) {
        Product p = Product.builder()
                .id(productId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name("Pomada").price(new BigDecimal("29.90")).active(true)
                .trackStock(trackStock).stockQuantity(stock).minStock(min)
                .build();
        when(productRepository.findByIdAndBarbershopId(eq(productId), any())).thenReturn(Optional.of(p));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        return p;
    }

    // ---------- opt-in ----------

    @Test
    @DisplayName("produto sem controle nunca bloqueia venda, mesmo com saldo zero")
    void requireAvailable_untracked_neverBlocks() {
        Product p = product(false, 0, 0);

        service.requireAvailable(p, 999); // não lança
    }

    @Test
    @DisplayName("produto sem controle não movimenta estoque na venda")
    void registerSale_untracked_doesNothing() {
        Product p = product(false, 0, 0);

        service.registerSale(p, 3, UUID.randomUUID());

        verify(stockMovementRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // ---------- baixa na venda ----------

    @Test
    @DisplayName("venda de produto controlado baixa o saldo e grava o movimento")
    void registerSale_tracked_decrementsAndLogs() {
        Product p = product(true, 10, 0);
        UUID saleId = UUID.randomUUID();

        service.registerSale(p, 3, saleId);

        assertThat(p.getStockQuantity()).isEqualTo(7);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        StockMovement m = captor.getValue();
        assertThat(m.getType()).isEqualTo(StockMovementType.SAIDA);
        assertThat(m.getQuantity()).isEqualTo(3);
        assertThat(m.getResultingStock()).isEqualTo(7);
        assertThat(m.getProductSaleId()).isEqualTo(saleId);
    }

    @Test
    @DisplayName("venda acima do saldo é recusada e não movimenta nada")
    void requireAvailable_insufficient_throws() {
        Product p = product(true, 2, 0);

        assertThatThrownBy(() -> service.requireAvailable(p, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estoque insuficiente")
                .hasMessageContaining("restam 2");

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("vender exatamente o saldo disponível é permitido e zera o estoque")
    void registerSale_exactStock_isAllowed() {
        Product p = product(true, 3, 0);

        service.registerSale(p, 3, UUID.randomUUID());

        assertThat(p.getStockQuantity()).isZero();
    }

    // ---------- reposição, perda e ajuste ----------

    @Test
    @DisplayName("reposição soma ao saldo e liga o controle de estoque")
    void addStock_incrementsAndEnablesTracking() {
        Product p = product(false, 0, 0);

        Product saved = service.addStock(productId, 12, "Nota 123");

        assertThat(saved.getStockQuantity()).isEqualTo(12);
        assertThat(saved.isTrackStock()).isTrue(); // repor declara "eu conto este produto"
    }

    @Test
    @DisplayName("perda sai do estoque sem passar pelo caixa")
    void removeStock_decrements() {
        product(true, 10, 0);

        Product saved = service.removeStock(productId, 2, "Quebrou");

        assertThat(saved.getStockQuantity()).isEqualTo(8);
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getProductSaleId()).isNull(); // não é venda
    }

    @Test
    @DisplayName("perda acima do saldo é recusada")
    void removeStock_insufficient_throws() {
        product(true, 1, 0);

        assertThatThrownBy(() -> service.removeStock(productId, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("ajuste leva o saldo exatamente para a contagem física")
    void adjustTo_setsExactCount() {
        product(true, 10, 0);

        Product saved = service.adjustTo(productId, 7, "Contagem do mês");

        assertThat(saved.getStockQuantity()).isEqualTo(7);
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(StockMovementType.AJUSTE);
        assertThat(captor.getValue().getQuantity()).isEqualTo(3); // a diferença aplicada
    }

    @Test
    @DisplayName("ajuste para o mesmo saldo não gera movimento")
    void adjustTo_sameCount_noMovement() {
        product(true, 5, 0);

        service.adjustTo(productId, 5, null);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("contagem negativa é recusada")
    void adjustTo_negative_throws() {
        assertThatThrownBy(() -> service.adjustTo(productId, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- alerta de mínimo ----------

    @Test
    @DisplayName("saldo no nível mínimo marca o produto para reposição")
    void lowStock_atThreshold_isFlagged() {
        Product p = product(true, 10, 3);

        service.registerSale(p, 7, UUID.randomUUID()); // sobra 3, igual ao mínimo

        assertThat(p.getStockQuantity()).isEqualTo(3);
        assertThat(p.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("mínimo zero significa sem alerta, mesmo com estoque zerado")
    void lowStock_minZero_neverFlags() {
        Product p = product(true, 0, 0);

        assertThat(p.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("produto sem controle nunca entra no alerta de reposição")
    void lowStock_untracked_neverFlags() {
        Product p = product(false, 0, 5);

        assertThat(p.isLowStock()).isFalse();
    }

    // ---------- isolamento ----------

    @Test
    @DisplayName("produto de outra barbearia não é encontrado")
    void addStock_otherTenant_notFound() {
        when(productRepository.findByIdAndBarbershopId(eq(productId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addStock(productId, 1, null))
                .isInstanceOf(NotFoundException.class);
    }
}

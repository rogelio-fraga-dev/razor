package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Product;
import com.razor.api.model.ProductSale;
import com.razor.api.model.Professional;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Testes da venda de balcao (V21): congelamento de preco, caixa obrigatorio e isolamento. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSaleServiceTest {

    @Mock private ProductSaleRepository productSaleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CashRegisterService cashRegisterService;

    @InjectMocks private ProductSaleService service;

    private final UUID productId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product givenActiveProduct(String name, String price) {
        Product p = Product.builder()
                .id(productId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name(name).price(new BigDecimal(price)).active(true).build();
        when(productRepository.findByIdAndBarbershopId(eq(productId), any())).thenReturn(Optional.of(p));
        return p;
    }

    private void givenCashRegisterAccepts() {
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(UUID.randomUUID()).build());
        when(productSaleRepository.save(any(ProductSale.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("venda congela nome e preço do produto e calcula o total")
    void sell_freezesPriceAndComputesTotal() {
        givenActiveProduct("Pomada", "29.90");
        givenCashRegisterAccepts();

        ProductSale sale = service.sell(productId, 3, null, CashPaymentMethod.DINHEIRO);

        assertThat(sale.getProductName()).isEqualTo("Pomada");
        assertThat(sale.getUnitPrice()).isEqualByComparingTo("29.90");
        assertThat(sale.getQuantity()).isEqualTo(3);
        assertThat(sale.getTotal()).isEqualByComparingTo("89.70");
        assertThat(sale.getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(sale.getCashMovementId()).isNotNull();
    }

    @Test
    @DisplayName("venda lança o total no caixa como VENDA, com a forma de pagamento escolhida")
    void sell_postsTotalToCashRegister() {
        givenActiveProduct("Cerveja", "10.00");
        givenCashRegisterAccepts();

        service.sell(productId, 2, null, CashPaymentMethod.PIX);

        verify(cashRegisterService).recordMovement(
                eq(CashMovementType.VENDA), eq(CashPaymentMethod.PIX),
                eq(new BigDecimal("20.00")), eq("2x Cerveja"));
    }

    @Test
    @DisplayName("sem caixa aberto a venda inteira é recusada, sem gravar nada")
    void sell_withoutOpenCashRegister_isRejected() {
        givenActiveProduct("Pomada", "29.90");
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.sell(productId, 1, null, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caixa aberto");

        verify(productSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("produto inativo não pode ser vendido e não toca no caixa")
    void sell_inactiveProduct_isRejected() {
        Product inactive = Product.builder()
                .id(productId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name("Cera antiga").price(new BigDecimal("20.00")).active(false).build();
        when(productRepository.findByIdAndBarbershopId(eq(productId), any())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.sell(productId, 1, null, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inativo");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
        verify(productSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("quantidade não positiva é recusada antes de tocar no caixa")
    void sell_nonPositiveQuantity_isRejected() {
        givenActiveProduct("Pomada", "29.90");

        assertThatThrownBy(() -> service.sell(productId, 0, null, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sell(productId, -2, null, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("produto de outro tenant não é vendável")
    void sell_otherTenantProduct_notFound() {
        when(productRepository.findByIdAndBarbershopId(eq(productId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sell(productId, 1, null, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("barbeiro de outra barbearia é recusado")
    void sell_professionalFromAnotherShop_isRejected() {
        givenActiveProduct("Pomada", "29.90");
        UUID otherProf = UUID.randomUUID();
        when(professionalRepository.findById(otherProf)).thenReturn(Optional.of(
                Professional.builder().id(otherProf).barbershopId(UUID.randomUUID()).name("Intruso").build()));

        assertThatThrownBy(() -> service.sell(productId, 1, otherProf, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(NotFoundException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("barbeiro da própria barbearia entra na venda")
    void sell_professionalOfThisShop_isRecorded() {
        givenActiveProduct("Pomada", "29.90");
        givenCashRegisterAccepts();
        UUID prof = UUID.randomUUID();
        when(professionalRepository.findById(prof)).thenReturn(Optional.of(
                Professional.builder().id(prof).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                        .name("Rafael").build()));

        ProductSale sale = service.sell(productId, 1, prof, CashPaymentMethod.CREDITO);

        assertThat(sale.getProfessionalId()).isEqualTo(prof);
    }
}

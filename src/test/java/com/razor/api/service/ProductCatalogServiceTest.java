package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Product;
import com.razor.api.repository.ProductRepository;
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

/** Testes do catalogo de produtos do balcao (V20). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogServiceTest {

    @Mock private ProductRepository productRepository;

    @InjectMocks private ProductCatalogService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenNameIsFree() {
        when(productRepository.findByBarbershopIdAndNameIgnoreCase(any(), any())).thenReturn(Optional.empty());
    }

    private void givenSaveEchoes() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("cria produto ativo no tenant atual, com preço em escala 2")
    void create_savesActiveProductScoped() {
        givenNameIsFree();
        givenSaveEchoes();

        Product created = service.create("  Pomada Modeladora  ", new BigDecimal("29.909"));

        assertThat(created.getName()).isEqualTo("Pomada Modeladora"); // aparado
        assertThat(created.getPrice()).isEqualByComparingTo("29.91");
        assertThat(created.getPrice().scale()).isEqualTo(2);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
    }

    @Test
    @DisplayName("recusa nome vazio e preço não positivo, sem gravar nada")
    void create_rejectsInvalidInput() {
        givenNameIsFree();

        assertThatThrownBy(() -> service.create("   ", new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome");
        assertThatThrownBy(() -> service.create("Cerveja", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> service.create("Cerveja", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("Cerveja", null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("recusa nome repetido na mesma barbearia (mensagem legível, não erro do banco)")
    void create_rejectsDuplicateName() {
        when(productRepository.findByBarbershopIdAndNameIgnoreCase(any(), eq("Pomada")))
                .thenReturn(Optional.of(Product.builder().id(UUID.randomUUID()).name("Pomada").build()));

        assertThatThrownBy(() -> service.create("Pomada", new BigDecimal("30.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe um produto");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar o próprio produto não colide com o próprio nome")
    void update_sameNameOnItself_isAllowed() {
        UUID id = UUID.randomUUID();
        Product existing = Product.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name("Pomada").price(new BigDecimal("30.00")).active(true).build();
        when(productRepository.findByIdAndBarbershopId(eq(id), any())).thenReturn(Optional.of(existing));
        when(productRepository.findByBarbershopIdAndNameIgnoreCase(any(), eq("Pomada")))
                .thenReturn(Optional.of(existing));
        givenSaveEchoes();

        Product updated = service.update(id, "Pomada", new BigDecimal("35.00"), true);

        assertThat(updated.getPrice()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("produto de outro tenant não é encontrado para edição")
    void update_otherTenantProduct_notFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndBarbershopId(eq(id), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, "Pomada", new BigDecimal("30.00"), true))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("desativa em vez de apagar — o histórico de vendas continua com referência")
    void deactivate_keepsRowInactive() {
        UUID id = UUID.randomUUID();
        Product existing = Product.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name("Cerveja").price(new BigDecimal("10.00")).active(true).build();
        when(productRepository.findByIdAndBarbershopId(eq(id), any())).thenReturn(Optional.of(existing));
        givenSaveEchoes();

        Product deactivated = service.deactivate(id);

        assertThat(deactivated.isActive()).isFalse();
        verify(productRepository).save(existing);
        verify(productRepository, never()).delete(any());
    }
}

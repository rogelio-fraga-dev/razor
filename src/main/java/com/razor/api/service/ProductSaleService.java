package com.razor.api.service;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Product;
import com.razor.api.model.ProductSale;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Venda de balcao (V21). Registra a venda, congela nome e preco do produto e lanca o dinheiro
 * no caixa aberto, para a gaveta esperada do fechamento continuar batendo.
 *
 * <p><b>Exige caixa aberto.</b> Sem sessao aberta a venda e recusada: dinheiro que entra fora do
 * caixa nao aparece na conferencia da gaveta e vira falta no fechamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSaleService {

    private final ProductSaleRepository productSaleRepository;
    private final ProductRepository productRepository;
    private final ProfessionalRepository professionalRepository;
    private final CashRegisterService cashRegisterService;

    @Transactional
    public ProductSale sell(UUID productId, int quantity, UUID professionalId, CashPaymentMethod method) {
        UUID shop = TenantContext.getCurrent();

        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        if (method == null) {
            throw new IllegalArgumentException("Escolha a forma de pagamento da venda.");
        }

        Product product = productRepository.findByIdAndBarbershopId(productId, shop)
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado."));
        if (!product.isActive()) {
            throw new IllegalArgumentException("O produto \"" + product.getName() + "\" esta inativo.");
        }
        requireProfessionalOfThisShop(professionalId, shop);

        BigDecimal unitPrice = product.getPrice();
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // Lanca no caixa primeiro: se nao houver caixa aberto, a venda inteira e recusada.
        String description = quantity + "x " + product.getName();
        CashMovement movement =
                cashRegisterService.recordMovement(CashMovementType.VENDA, method, total, description);

        LocalDateTime now = LocalDateTime.now();
        ProductSale sale = productSaleRepository.save(ProductSale.builder()
                .barbershopId(shop)
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(unitPrice)
                .quantity(quantity)
                .total(total)
                .professionalId(professionalId)
                .paymentMethod(method)
                .cashMovementId(movement.getId())
                .saleDate(AppointmentDates.today())
                .soldAt(now)
                .build());

        log.info("[VENDA] {} por {} ({}) no tenant {}", description, total, method, shop);
        return sale;
    }

    /** Vendas de um dia (padrao: hoje), da mais recente para a mais antiga. */
    public List<ProductSale> salesOn(LocalDate date) {
        return productSaleRepository.findByBarbershopIdAndSaleDateOrderBySoldAtDesc(
                TenantContext.getCurrent(), date);
    }

    /** Isolamento: barbeiro de outra barbearia (ou inexistente) nao entra na venda. */
    private void requireProfessionalOfThisShop(UUID professionalId, UUID shop) {
        if (professionalId == null) return;
        professionalRepository.findById(professionalId)
                .filter(p -> shop.equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));
    }
}

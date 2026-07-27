package com.razor.api.controller;

import com.razor.api.dto.ProductSaleRequest;
import com.razor.api.dto.ProductSaleResponse;
import com.razor.api.model.Professional;
import com.razor.api.model.ProductSale;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.ProductSaleService;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Venda de balcao no painel web (tenant vem do JWT). A venda exige caixa aberto e lanca o
 * dinheiro nele — ver {@link ProductSaleService}.
 */
@RestController
@RequestMapping("/api/admin/product-sales")
@RequiredArgsConstructor
public class AdminProductSaleController {

    private final ProductSaleService productSaleService;
    private final ProfessionalRepository professionalRepository;

    /** Vendas de um dia (padrao: hoje). Ex.: {@code /product-sales?date=2026-07-27}. */
    @GetMapping
    public List<ProductSaleResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : AppointmentDates.today();
        return toResponses(productSaleService.salesOn(target));
    }

    @PostMapping
    public ResponseEntity<ProductSaleResponse> sell(@Valid @RequestBody ProductSaleRequest req) {
        ProductSale sale = productSaleService.sell(
                req.productId(), req.quantity(), req.professionalId(), req.paymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponses(List.of(sale)).get(0));
    }

    /** Resolve o nome do barbeiro de uma vez so, em vez de uma consulta por venda. */
    private List<ProductSaleResponse> toResponses(List<ProductSale> sales) {
        Map<UUID, String> names = professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
        return sales.stream()
                .map(s -> ProductSaleResponse.from(
                        s, s.getProfessionalId() == null ? null : names.get(s.getProfessionalId())))
                .toList();
    }
}

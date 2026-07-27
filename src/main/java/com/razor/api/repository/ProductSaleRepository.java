package com.razor.api.repository;

import com.razor.api.model.ProductSale;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSaleRepository extends JpaRepository<ProductSale, UUID> {

    List<ProductSale> findByBarbershopIdAndSaleDateOrderBySoldAtDesc(UUID barbershopId, LocalDate saleDate);

    /** Base da comissao de produto no periodo (inclusive nas duas pontas). */
    List<ProductSale> findByBarbershopIdAndSaleDateBetween(UUID barbershopId, LocalDate start, LocalDate end);
}

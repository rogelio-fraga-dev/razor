package com.razor.api.repository;

import com.razor.api.model.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByBarbershopId(UUID barbershopId);

    List<Product> findByBarbershopIdAndActiveTrue(UUID barbershopId);

    /** Usado na checagem de nome repetido — o indice unico do banco e por nome minusculo. */
    Optional<Product> findByBarbershopIdAndNameIgnoreCase(UUID barbershopId, String name);

    /** Leitura escopada no tenant: evita ler produto de outra barbearia por id adivinhado. */
    Optional<Product> findByIdAndBarbershopId(UUID id, UUID barbershopId);
}

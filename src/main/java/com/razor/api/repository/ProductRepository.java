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

    /**
     * Nome sem diferenca de maiusculas, com {@code lower(name)} — a MESMA expressao do indice
     * unico do banco, para a busca usar o indice (o derivado "IgnoreCase" gera {@code upper()}).
     */
    @org.springframework.data.jpa.repository.Query(
            "select x from Product x where x.barbershopId = :shop and lower(x.name) = lower(:name)")
    Optional<Product> findByBarbershopIdAndNameIgnoreCase(
            @org.springframework.data.repository.query.Param("shop") UUID barbershopId,
            @org.springframework.data.repository.query.Param("name") String name);

    /** Leitura escopada no tenant: evita ler produto de outra barbearia por id adivinhado. */
    Optional<Product> findByIdAndBarbershopId(UUID id, UUID barbershopId);
}

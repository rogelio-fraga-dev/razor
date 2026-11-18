package com.razor.api.repository;

import com.razor.api.model.ServicePackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

    List<ServicePackage> findByBarbershopIdOrderByNameAsc(UUID barbershopId);

    Optional<ServicePackage> findByIdAndBarbershopId(UUID id, UUID barbershopId);

    /**
     * Nome sem diferenca de maiusculas, com {@code lower(name)} — a MESMA expressao do indice
     * unico do banco, para a busca usar o indice (o derivado "IgnoreCase" gera {@code upper()}).
     */
    @org.springframework.data.jpa.repository.Query(
            "select x from ServicePackage x where x.barbershopId = :shop and lower(x.name) = lower(:name)")
    Optional<ServicePackage> findByBarbershopIdAndNameIgnoreCase(
            @org.springframework.data.repository.query.Param("shop") UUID barbershopId,
            @org.springframework.data.repository.query.Param("name") String name);
}

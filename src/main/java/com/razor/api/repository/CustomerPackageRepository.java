package com.razor.api.repository;

import com.razor.api.model.CustomerPackage;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerPackageRepository extends JpaRepository<CustomerPackage, UUID> {

    List<CustomerPackage> findByBarbershopIdOrderByPurchasedAtDesc(UUID barbershopId);

    List<CustomerPackage> findByBarbershopIdAndCustomerIdOrderByPurchasedAtDesc(UUID barbershopId, UUID customerId);

    /** Pacotes com saldo e dentro da validade — o que ainda pode ser usado. */
    @Query("""
            select cp from CustomerPackage cp
             where cp.barbershopId = :shop
               and cp.remainingUses > 0
               and cp.expiresAt >= :today
            """)
    List<CustomerPackage> findUsable(@Param("shop") UUID barbershopId, @Param("today") LocalDate today);

    /**
     * Os mesmos pacotes usaveis, restritos a clientes e <b>travados para escrita</b>: duas
     * comandas do mesmo cliente fechando ao mesmo tempo nao podem gastar o mesmo uso.
     * Ordem: o que vence primeiro e gasto primeiro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cp from CustomerPackage cp
             where cp.barbershopId = :shop
               and cp.customerId in :customers
               and cp.remainingUses > 0
               and cp.expiresAt >= :today
             order by cp.expiresAt asc, cp.purchasedAt asc
            """)
    List<CustomerPackage> lockUsableForCustomers(@Param("shop") UUID barbershopId,
                                                 @Param("customers") Collection<UUID> customerIds,
                                                 @Param("today") LocalDate today);
}

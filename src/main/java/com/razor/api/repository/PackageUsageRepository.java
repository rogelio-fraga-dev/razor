package com.razor.api.repository;

import com.razor.api.model.PackageUsage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackageUsageRepository extends JpaRepository<PackageUsage, UUID> {

    boolean existsByComandaItemId(UUID comandaItemId);

    List<PackageUsage> findByCustomerPackageIdOrderByUsedAtDesc(UUID customerPackageId);

    /** Usos ligados a agendamentos — a comissao troca o preco de tabela pelo valor do pacote. */
    List<PackageUsage> findByBarbershopIdAndAppointmentIdIn(UUID barbershopId, Collection<Long> appointmentIds);
}

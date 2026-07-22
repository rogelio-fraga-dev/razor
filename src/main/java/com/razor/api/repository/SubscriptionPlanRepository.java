package com.razor.api.repository;

import com.razor.api.model.SubscriptionPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findByBarbershopId(UUID barbershopId);

    List<SubscriptionPlan> findByBarbershopIdAndActiveTrue(UUID barbershopId);
}

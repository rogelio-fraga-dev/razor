package com.razor.api.repository;

import com.razor.api.model.PlanBenefit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanBenefitRepository extends JpaRepository<PlanBenefit, UUID> {

    List<PlanBenefit> findByPlanId(UUID planId);

    List<PlanBenefit> findByBarbershopId(UUID barbershopId);
}

package com.razor.api.repository;

import com.razor.api.model.ScheduleBlock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID> {

    List<ScheduleBlock> findByBarbershopId(UUID barbershopId);
}

package com.razor.api.repository;

import com.razor.api.model.BarbershopSite;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarbershopSiteRepository extends JpaRepository<BarbershopSite, UUID> {
}

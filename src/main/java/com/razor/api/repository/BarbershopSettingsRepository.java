package com.razor.api.repository;

import com.razor.api.model.BarbershopSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarbershopSettingsRepository extends JpaRepository<BarbershopSettings, UUID> {
}

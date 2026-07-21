package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.Barbershop;
import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida a fundacao multi-tenant (V4) contra Postgres real: o schema migrado bate
 * com as entidades ({@code ddl-auto=validate}), o tenant padrao e semeado pela
 * migracao, e Barbershop/Professional/ServiceOffering fazem round-trip.
 *
 * Pulado sem Docker (dev local); roda no CI.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Fundação multi-tenant (V4) contra Postgres real")
class MultiTenantPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("razor.openai.api-key", () -> "test-key");
        registry.add("razor.admin.phones", () -> "5511900000000");
    }

    @Autowired private BarbershopRepository barbershopRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;

    @Test
    @DisplayName("a migração V4 semeia o tenant padrão (slug 'figaro')")
    void defaultTenant_isSeeded() {
        Barbershop figaro = barbershopRepository.findBySlug("figaro").orElseThrow();

        assertThat(figaro.getId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(figaro.getWhatsappInstance()).isEqualTo("BarberBotOficial");
        assertThat(figaro.isActive()).isTrue();
    }

    @Test
    @DisplayName("a migração V5 semeia o catálogo de serviços do tenant padrão")
    void defaultTenant_hasSeededServices() {
        var services = serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.DEFAULT_BARBERSHOP_ID);

        assertThat(services).hasSizeGreaterThanOrEqualTo(3);
        assertThat(services).extracting(ServiceOffering::getName).contains("Corte de Cabelo", "Barba");
    }

    @Test
    @DisplayName("a migração V7 semeia o profissional padrão do tenant padrão")
    void defaultTenant_hasSeededProfessional() {
        var pros = professionalRepository.findByBarbershopIdAndActiveTrue(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(pros).extracting(Professional::getName).contains("Barbeiro");
    }

    @Test
    @DisplayName("Professional e ServiceOffering fazem round-trip com o tenant")
    void professionalAndService_roundTrip() {
        UUID shopId = TenantContext.DEFAULT_BARBERSHOP_ID;

        Professional prof = professionalRepository.save(Professional.builder()
                .barbershopId(shopId)
                .name("Barbeiro Teste")
                .phone("5511911112222")
                .build());

        ServiceOffering svc = serviceOfferingRepository.save(ServiceOffering.builder()
                .barbershopId(shopId)
                .name("Corte Visagista")
                .price(new BigDecimal("75.00"))
                .durationMinutes(45)
                .build());

        assertThat(professionalRepository.findByBarbershopIdAndActiveTrue(shopId))
                .extracting(Professional::getName).contains("Barbeiro Teste");

        ServiceOffering found = serviceOfferingRepository.findById(svc.getId()).orElseThrow();
        assertThat(found.getPrice()).isEqualByComparingTo("75.00");
        assertThat(found.getDurationMinutes()).isEqualTo(45);
        assertThat(found.isActive()).isTrue();

        assertThat(prof.getId()).isNotNull();
    }
}

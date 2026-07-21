package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * Valida que o schema migrado pelo Flyway bate com a entidade {@link Appointment}
 * ({@code ddl-auto=validate} nao pode falhar) e que o mapeamento dos tipos fortes
 * (LocalDate / LocalTime / PaymentStatus enum) faz round-trip contra um Postgres real.
 *
 * Pulado automaticamente quando nao ha Docker (dev local), roda no CI.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Persistencia JPA de Appointment (tipos fortes) contra Postgres real")
class AppointmentPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Datasource do container
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Segredos dummy: o slice carrega @Configuration (OpenAIConfig/RazorProperties),
        // e o application.yml usa ${OPENAI_API_KEY}/${ADMIN_PHONES} sem default.
        registry.add("razor.openai.api-key", () -> "test-key");
        registry.add("razor.admin.phones", () -> "5511900000000");
    }

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    @DisplayName("ddl-auto=validate passa e a entidade faz round-trip com tipos fortes")
    void schemaValidates_andEntityRoundTrips() {
        Appointment saved = appointmentRepository.save(Appointment.builder()
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .customerPhone("5511999998888")
                .customerName("Cliente Teste")
                .appointmentDate(LocalDate.of(2030, 3, 15))
                .appointmentTime(LocalTime.of(9, 5))
                .service("Corte")
                .paymentStatus(PaymentStatus.PIX)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull(); // @PrePersist preencheu

        Appointment found = appointmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAppointmentDate()).isEqualTo(LocalDate.of(2030, 3, 15));
        assertThat(found.getAppointmentTime()).isEqualTo(LocalTime.of(9, 5));
        assertThat(found.getPaymentStatus()).isEqualTo(PaymentStatus.PIX);
        assertThat(found.getService()).isEqualTo("Corte");
    }

    @Test
    @DisplayName("query derivada por LocalDate funciona (coluna e DATE tipada)")
    void findByAppointmentDate_typedColumn() {
        LocalDate day = LocalDate.of(2031, 1, 20);
        appointmentRepository.save(Appointment.builder()
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .customerPhone("5511777776666")
                .appointmentDate(day)
                .appointmentTime(LocalTime.of(14, 0))
                .paymentStatus(PaymentStatus.PENDENTE)
                .build());

        List<Appointment> found =
                appointmentRepository.findByBarbershopIdAndAppointmentDate(TenantContext.DEFAULT_BARBERSHOP_ID, day);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(a -> assertThat(a.getAppointmentDate()).isEqualTo(day));
    }

    @Test
    @DisplayName("paymentStatus default e PENDENTE quando nao informado")
    void paymentStatus_defaultsToPendente() {
        Appointment saved = appointmentRepository.save(Appointment.builder()
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .customerPhone("5511555554444")
                .appointmentDate(LocalDate.of(2032, 6, 1))
                .appointmentTime(LocalTime.of(11, 30))
                .build());

        Appointment found = appointmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPaymentStatus()).isEqualTo(PaymentStatus.PENDENTE);
    }
}

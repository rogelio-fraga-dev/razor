package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerTag;
import com.razor.api.model.CustomerTagLink;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.BarbershopSettingsRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.CustomerTagLinkRepository;
import com.razor.api.repository.CustomerTagRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V36 contra Postgres real: as consultas JPQL novas (horarios futuros do cliente, busca restrita a
 * um recorte de ids) e o mapeamento das tags passam pelo {@code ddl-auto=validate}.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V36: horarios futuros, tags e recorte da base contra Postgres real")
class CustomerSegmentPersistenceIT {

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

    private static final UUID SHOP = TenantContext.DEFAULT_BARBERSHOP_ID;
    private static final String PHONE = "5511944443333";

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerTagRepository tagRepository;
    @Autowired private CustomerTagLinkRepository linkRepository;
    @Autowired private BarbershopSettingsRepository settingsRepository;

    private Appointment appt(LocalDate d, LocalTime t) {
        return appointmentRepository.save(Appointment.builder()
                .barbershopId(SHOP).customerPhone(PHONE).appointmentDate(d).appointmentTime(t).build());
    }

    @Test
    @DisplayName("findUpcomingByCustomer: so de agora em diante, do mais proximo ao mais distante")
    void upcoming_onlyFutureOrdered() {
        LocalDate hoje = LocalDate.of(2031, 5, 10);
        appt(hoje.minusDays(30), LocalTime.of(10, 0));          // historico
        appt(hoje, LocalTime.of(9, 0));                          // hoje, ja passou
        Appointment hojeTarde = appt(hoje, LocalTime.of(16, 0)); // hoje, ainda vai
        Appointment semana = appt(hoje.plusDays(7), LocalTime.of(8, 0));
        Appointment amanha = appt(hoje.plusDays(1), LocalTime.of(11, 0));

        List<Appointment> out = appointmentRepository.findUpcomingByCustomer(SHOP, java.util.List.of(PHONE), hoje, LocalTime.of(12, 0));

        assertThat(out).extracting(Appointment::getId)
                .containsExactly(hojeTarde.getId(), amanha.getId(), semana.getId());
    }

    @Test
    @DisplayName("tags: vinculo N:N e busca restrita aos ids do recorte")
    void tags_andSearchAmong() {
        Customer ana = customerRepository.save(Customer.builder().barbershopId(SHOP).phoneNumber("5511900001111").name("Ana").build());
        Customer bia = customerRepository.save(Customer.builder().barbershopId(SHOP).phoneNumber("5511900002222").name("Bia").build());
        CustomerTag vip = tagRepository.save(CustomerTag.builder().barbershopId(SHOP).name("VIP").color("#DC2626").build());
        linkRepository.save(new CustomerTagLink(ana.getId(), vip.getId()));

        assertThat(linkRepository.findCustomerIdsByTagId(vip.getId())).containsExactly(ana.getId());
        assertThat(tagRepository.findByBarbershopIdAndNameIgnoreCase(SHOP, "vip")).isPresent();

        var page = customerRepository.searchAmong(SHOP, List.of(ana.getId(), bia.getId()), "%bi%", false, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Customer::getName).containsExactly("Bia");

        linkRepository.deleteByTagId(vip.getId());
        assertThat(linkRepository.findByCustomerId(ana.getId())).isEmpty();
    }

    @Test
    @DisplayName("limite de agendamentos: coluna mapeada com default 2 na linha nova")
    void bookingLimitColumn() {
        var s = settingsRepository.save(com.razor.api.model.BarbershopSettings.builder().barbershopId(SHOP).build());
        assertThat(settingsRepository.findById(SHOP).orElseThrow().getMaxFutureBookingsPerCustomer()).isEqualTo(2);
        s.setMaxFutureBookingsPerCustomer(null);
        settingsRepository.saveAndFlush(s);
        assertThat(settingsRepository.findById(SHOP).orElseThrow().getMaxFutureBookingsPerCustomer()).isNull();
    }
}

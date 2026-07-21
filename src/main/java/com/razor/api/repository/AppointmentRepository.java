package com.razor.api.repository;

import com.razor.api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Acesso escopado por tenant (Fase 1).
    List<Appointment> findByBarbershopId(UUID barbershopId);

    List<Appointment> findByBarbershopIdAndCustomerPhone(UUID barbershopId, String customerPhone);

    List<Appointment> findByBarbershopIdAndAppointmentDate(UUID barbershopId, LocalDate appointmentDate);
}

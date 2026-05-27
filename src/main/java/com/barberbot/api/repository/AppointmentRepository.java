package com.barberbot.api.repository;

import com.barberbot.api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    List<Appointment> findByCustomerPhone(String customerPhone);
    
    List<Appointment> findByAppointmentDate(String appointmentDate);

    List<Appointment> findByAppointmentDateAndAppointmentTime(String appointmentDate, String appointmentTime);
}

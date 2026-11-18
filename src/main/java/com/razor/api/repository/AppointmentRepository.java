package com.razor.api.repository;

import com.razor.api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Acesso escopado por tenant (Fase 1).
    List<Appointment> findByBarbershopId(UUID barbershopId);

    /** Leitura por id escopada no tenant: id adivinhado de outra barbearia nao e encontrado. */
    java.util.Optional<Appointment> findByIdAndBarbershopId(Long id, UUID barbershopId);

    List<Appointment> findByBarbershopIdAndCustomerPhone(UUID barbershopId, String customerPhone);

    List<Appointment> findByBarbershopIdAndAppointmentDate(UUID barbershopId, LocalDate appointmentDate);

    /** Intervalo filtrado no banco. Sem isto os relatorios liam a tabela inteira do tenant. */
    List<Appointment> findByBarbershopIdAndAppointmentDateBetween(UUID barbershopId, LocalDate from, LocalDate to);

    // Agenda multiprofissional: ocupacao por profissional (Fase 1).
    List<Appointment> findByBarbershopIdAndProfessionalId(UUID barbershopId, UUID professionalId);

    List<Appointment> findByBarbershopIdAndProfessionalIdAndAppointmentDate(
            UUID barbershopId, UUID professionalId, LocalDate appointmentDate);

    /**
     * Horarios AINDA POR VIR de um cliente, do mais proximo ao mais distante.
     *
     * <p>Existe porque {@link #findByBarbershopIdAndCustomerPhone} devolve o historico inteiro, e
     * o bot usava aquilo como "agendamentos ativos": o cliente fiel ouvia "limite atingido", o
     * "cancelar meu horario" listava (e apagava) atendimentos ja pagos, e a escolha de pagamento
     * podia cair num horario antigo. Hoje conta a partir de {@code now} — o corte que comecou ha
     * dez minutos ja nao e "futuro". Horario nulo (legado) no dia de hoje conta como futuro: nao
     * da para saber, e some no dia seguinte.
     */
    @Query("""
            select a from Appointment a
             where a.barbershopId = :shop
               and a.customerPhone = :phone
               and (a.appointmentDate > :today
                    or (a.appointmentDate = :today
                        and (a.appointmentTime is null or a.appointmentTime >= :now)))
             order by a.appointmentDate asc, a.appointmentTime asc
            """)
    List<Appointment> findUpcomingByCustomer(@Param("shop") UUID barbershopId,
                                             @Param("phone") String customerPhone,
                                             @Param("today") LocalDate today,
                                             @Param("now") LocalTime now);
}

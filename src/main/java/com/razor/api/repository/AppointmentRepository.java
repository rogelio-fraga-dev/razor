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

    /** De uma data em diante — o contexto de ocupacao da IA nao precisa do passado. */
    List<Appointment> findByBarbershopIdAndAppointmentDateGreaterThanEqual(UUID barbershopId, LocalDate from);

    /** Todos os agendamentos destes telefones (grafias do mesmo numero, ver PhoneNumbers.variants). */
    List<Appointment> findByBarbershopIdAndCustomerPhoneIn(UUID barbershopId, java.util.Collection<String> customerPhones);

    /**
     * Agregado por telefone para o status do cliente: primeira e ultima visita e quantas — tudo
     * no banco, sem carregar a tabela. So agendamentos ate {@code today} (futuro nao e visita).
     * Cada linha: {@code [customer_phone, min(date), max(date), count]}.
     */
    @Query("""
            select a.customerPhone, min(a.appointmentDate), max(a.appointmentDate), count(a)
              from Appointment a
             where a.barbershopId = :shop
               and a.customerPhone is not null
               and a.appointmentDate is not null
               and a.appointmentDate <= :today
             group by a.customerPhone
            """)
    List<Object[]> visitStatsByPhone(@Param("shop") UUID barbershopId, @Param("today") LocalDate today);

    /**
     * Agendamentos com barbeiro num periodo (comissao) — filtrado no banco pelo indice
     * (barbershop_id, professional_id, appointment_date), em vez de ler o tenant inteiro.
     */
    @Query("""
            select a from Appointment a
             where a.barbershopId = :shop
               and a.professionalId is not null
               and a.appointmentDate between :start and :end
            """)
    List<Appointment> findWithProfessionalBetween(@Param("shop") UUID barbershopId,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    /** Intervalo filtrado no banco. Sem isto os relatorios liam a tabela inteira do tenant. */
    List<Appointment> findByBarbershopIdAndAppointmentDateBetween(UUID barbershopId, LocalDate from, LocalDate to);

    /** Agendamentos CRIADOS no intervalo [from, to) — base do card "O que a IA fez por voce" (V42). */
    List<Appointment> findByBarbershopIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID barbershopId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** Faltas do cliente a partir de uma data — regra do sinal (V42). */
    long countByBarbershopIdAndCustomerPhoneAndAttendanceStatusAndAppointmentDateGreaterThanEqual(
            UUID barbershopId, String customerPhone, com.razor.api.model.AttendanceStatus attendanceStatus,
            LocalDate since);

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
     *
     * <p>Recebe as GRAFIAS do telefone ({@code PhoneNumbers.variants}): o mesmo cliente pode estar
     * gravado com 12 ou 13 digitos (com/sem o nono digito) conforme o canal que o cadastrou.
     */
    @Query("""
            select a from Appointment a
             where a.barbershopId = :shop
               and a.customerPhone in :phones
               and (a.appointmentDate > :today
                    or (a.appointmentDate = :today
                        and (a.appointmentTime is null or a.appointmentTime >= :now)))
             order by a.appointmentDate asc, a.appointmentTime asc
            """)
    List<Appointment> findUpcomingByCustomer(@Param("shop") UUID barbershopId,
                                             @Param("phones") java.util.Collection<String> customerPhones,
                                             @Param("today") LocalDate today,
                                             @Param("now") LocalTime now);
}

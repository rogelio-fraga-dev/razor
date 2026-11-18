package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Operacoes de escrita e consulta de agendamentos de um cliente, vindas do bot (tags da IA) e
 * dos comandos do admin no WhatsApp.
 *
 * <ul>
 *   <li><b>Criar</b>: {@link #adicionarAgendamentoViaIA} (atribui barbeiro e cria lembrete/avaliacao),
 *       {@link #agendarPeloPainel} (agenda interativa) e {@link #processAgenda} (lote em JSON do admin).
 *       O ponto de fidelidade vem no fechamento da comanda, nao na reserva.</li>
 *   <li><b>Cancelar</b>: {@link #cancelarAgendamentoExato} / {@link #cancelarAgendamentoCliente},
 *       que tambem cancelam as tarefas pendentes do telefone.</li>
 *   <li><b>Consultar</b>: agendamentos do cliente e se ele tem horario futuro ativo.</li>
 * </ul>
 * Tarefas agendadas ficam no {@link AgendaTaskService}. Extraido do antigo {@link AgendaService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaBookingService {

    private static final ZoneId ZONE = AppointmentDates.ZONE;

    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;
    private final ProfessionalAssignmentService professionalAssignmentService;
    private final AgendaTaskService agendaTaskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<Appointment> adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        return adicionarAgendamentoViaIA(name, phone, date, time, service, null);
    }

    /**
     * Cria um agendamento e atribui um profissional (barbeiro): usa o {@code preferredProfessionalId}
     * se informado e livre; caso contrario auto-atribui o barbeiro livre menos ocupado no dia.
     *
     * @return o agendamento salvo; vazio se nada foi agendado (ex.: data/hora que nao da para
     *         interpretar). Quem chama SO pode confirmar ao cliente quando houver agendamento.
     */
    public Optional<Appointment> adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service,
                                          UUID preferredProfessionalId) {
        LocalDate parsedDate;
        LocalTime parsedTime;
        try {
            parsedDate = AppointmentDates.parseDate(date);
            parsedTime = AppointmentDates.parseTime(time);
        } catch (Exception e) {
            log.error("[AGENDA] Nao foi possivel criar agendamento para {} — data/hora invalida ('{}' '{}'): {}", phone, date, time, e.getMessage());
            return Optional.empty();
        }

        UUID professionalId = professionalAssignmentService.assign(parsedDate, parsedTime, preferredProfessionalId, service);

        Appointment app = Appointment.builder()
            .barbershopId(TenantContext.getCurrent())
            .customerName(name)
            .customerPhone(phone)
            .appointmentDate(parsedDate)
            .appointmentTime(parsedTime)
            .service(service)
            .professionalId(professionalId)
            .paymentStatus(PaymentStatus.PENDENTE)
            .build();
        if (professionalId != null) {
            log.info("[AGENDA] Profissional {} atribuido ao agendamento de {}", professionalId, phone);
        }
        return Optional.of(registrarNovoAgendamento(app));
    }

    /**
     * Agendamento criado pela recepcao/dono no painel (agenda interativa): o barbeiro e o horario
     * vem escolhidos e ja validados pelo {@link AgendaPanelService} — aqui nao ha auto-atribuicao.
     *
     * <p>Passa pelo MESMO caminho de gravacao do agendamento da IA ({@link #registrarNovoAgendamento}):
     * lembrete e avaliacao iguais, e qualquer regra nova de criacao (ex.: limite
     * de agendamentos por cliente) colocada ali vale para os dois canais.
     */
    public Appointment agendarPeloPainel(String name, String phone, LocalDate date, LocalTime time,
                                         String service, UUID professionalId) {
        Appointment app = Appointment.builder()
            .barbershopId(TenantContext.getCurrent())
            .customerName(name)
            .customerPhone(phone)
            .appointmentDate(date)
            .appointmentTime(time)
            .service(service)
            .professionalId(professionalId)
            .paymentStatus(PaymentStatus.PENDENTE)
            .build();
        return registrarNovoAgendamento(app);
    }

    /** Grava um agendamento novo e dispara o que nasce com ele (lembrete e pedido de avaliacao). */
    private Appointment registrarNovoAgendamento(Appointment app) {
        Appointment saved = appointmentRepository.save(app);
        // save() com mock (testes) devolve null; com JPA devolve a mesma instancia com o id.
        Appointment ag = saved != null ? saved : app;

        log.info("[AGENDA] Novo agendamento salvo: {} para o dia {} às {}", ag.getCustomerName(),
                AppointmentDates.formatDate(ag.getAppointmentDate()), AppointmentDates.formatTime(ag.getAppointmentTime()));

        agendaTaskService.criarTarefasAgendadas(ag);

        // Fidelidade NAO e dada aqui: agendar nao e ser atendido (cancelamento nunca estornava o
        // ponto). O ponto vem no fechamento da comanda — ver FidelityVisitService.
        return ag;
    }

    /**
     * Lote de agendamentos enviado pelo admin ({@code {"items":[{name,time,date,service}]}}).
     * Retorna quantos foram de clientes conhecidos (com telefone, e portanto com lembrete).
     *
     * <p>Erro (JSON invalido da Visao IA, falha de banco) <b>sobe</b> e o lote inteiro e desfeito:
     * antes o catch devolvia 0 e o admin lia "✅ 0 clientes", sem saber que a importacao falhou.
     */
    @org.springframework.transaction.annotation.Transactional
    public int processAgenda(String jsonAgenda) {
        int agendados = 0;
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonAgenda);
        } catch (Exception e) {
            log.error("[AGENDA] JSON de agenda invalido recebido: '{}'", truncate(jsonAgenda), e);
            throw new IllegalArgumentException("JSON de agenda invalido", e);
        }
        try {
            JsonNode items = root == null ? null : root.get("items");
            if (items == null || !items.isArray()) {
                log.warn("[AGENDA] JSON de agenda sem lista 'items': '{}'", truncate(jsonAgenda));
            }

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String name = item.has("name") ? item.get("name").asText() : "";
                    String time = item.has("time") ? item.get("time").asText() : "";
                    String date = item.has("date") ? item.get("date").asText() : "hoje";
                    String service = item.has("service") ? item.get("service").asText() : "Corte";

                    if (!name.isEmpty() && !time.isEmpty()) {
                        LocalDate parsedDate;
                        LocalTime parsedTime;
                        try {
                            parsedDate = AppointmentDates.parseDate(date);
                            parsedTime = AppointmentDates.parseTime(time);
                        } catch (Exception e) {
                            log.warn("[AGENDA] Item de agenda ignorado (data/hora invalida: '{}' '{}'): {}", date, time, e.getMessage());
                            continue;
                        }

                        Customer customer = customerService.findByName(name);
                        String phone = (customer != null) ? customer.getPhoneNumber() : null;

                        Appointment app = Appointment.builder()
                            .barbershopId(TenantContext.getCurrent())
                            .customerName(name)
                            .customerPhone(phone != null ? phone : "N/A")
                            .appointmentTime(parsedTime)
                            .appointmentDate(parsedDate)
                            .service(service)
                            .paymentStatus(PaymentStatus.PENDENTE)
                            .build();

                        appointmentRepository.save(app);

                        if (phone != null) {
                            agendados++;
                            agendaTaskService.criarTarefasAgendadas(app);
                        }
                    }
                }
            }
            return agendados;
        } catch (RuntimeException e) {
            log.error("[AGENDA] Falha ao importar o lote de agenda (desfeito). JSON: '{}'", truncate(jsonAgenda), e);
            throw e;
        }
    }

    /** JSON no log sem estourar a linha (a Visao IA pode devolver textos longos). */
    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 500 ? s : s.substring(0, 500) + "...(" + s.length() + " chars)";
    }

    public Appointment getAgendamentoCliente(String phone) {
        return appointmentRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone).stream().findFirst().orElse(null);
    }

    public List<Appointment> getAgendamentosClienteList(String phone) {
        return appointmentRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);
    }

    public boolean hasAgendamentoFuturo(String phone) {
        LocalDateTime agora = LocalDateTime.now(ZONE);
        LocalDate hoje = agora.toLocalDate();
        List<Appointment> apps = appointmentRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);

        for (Appointment ag : apps) {
            LocalDate agDate = ag.getAppointmentDate();
            if (agDate == null) continue;

            if (agDate.isAfter(hoje)) {
                return true;
            } else if (agDate.isEqual(hoje)) {
                LocalTime agTime = ag.getAppointmentTime();
                // Se o horário atual for ANTES do (horário agendado + 1 hora), consideramos como futuro/ativo.
                // Sem horário válido, consideramos ativo hoje por segurança.
                if (agTime == null || agora.toLocalTime().isBefore(agTime.plusHours(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean cancelarAgendamentoExato(Appointment ag) {
        if (ag != null) {
            // Tarefas antes do delete: o reconhecimento das tarefas legadas usa o horario dele.
            // So as DESTE horario — os lembretes dos outros agendamentos do cliente ficam.
            agendaTaskService.cancelarTarefasDoAgendamento(ag);
            appointmentRepository.delete(ag);
            return true;
        }
        return false;
    }

    public boolean cancelarAgendamentoCliente(String phone) {
        Appointment ag = getAgendamentoCliente(phone);
        if (ag != null) {
            agendaTaskService.cancelarTarefasDoAgendamento(ag);
            appointmentRepository.delete(ag);
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            return true;
        }
        return false;
    }
}

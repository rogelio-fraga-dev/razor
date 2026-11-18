package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final AgendaAvailabilityService agendaAvailabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Pedido de agendamento, venha de onde vier (bot, painel, site).
     *
     * @param durationMinutes         duracao do servico; {@code null} = pela tabela do catalogo
     * @param preferredProfessionalId barbeiro pedido, ou {@code null}
     * @param exigirPreferido         {@code true} = recusa se o pedido estiver ocupado, em vez de
     *                                atribuir outro (site: o cliente escolheu o barbeiro na grade)
     */
    public record NovoAgendamento(String name, String phone, LocalDate date, LocalTime time, String service,
                                  Integer durationMinutes, UUID preferredProfessionalId,
                                  boolean exigirPreferido, AppointmentSource source) {
    }

    /**
     * Trava por barbearia em volta de "checar e gravar". Sem ela, duas requisicoes simultaneas
     * (o site e o bot, ou dois visitantes) leem a mesma cadeira livre e as duas gravam. Em memoria:
     * resolve o caso real (uma instancia). Com varias instancias, trocar por lock no banco.
     */
    private final Map<UUID, Object> travas = new ConcurrentHashMap<>();

    public Optional<Appointment> adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        return adicionarAgendamentoViaIA(name, phone, date, time, service, null);
    }

    /**
     * Cria um agendamento vindo do bot e atribui um profissional (barbeiro): usa o
     * {@code preferredProfessionalId} se informado e livre; caso contrario auto-atribui o barbeiro
     * livre menos ocupado no dia.
     *
     * @return o agendamento gravado — vazio quando a data/hora e invalida ou o horario nao cabe
     *         mais (overbooking). Quem chama so confirma ao cliente se veio preenchido, e usa o
     *         {@code professionalId} DEVOLVIDO (pode nao ser o preferido).
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
        return agendar(new NovoAgendamento(name, phone, parsedDate, parsedTime, service, null,
                preferredProfessionalId, false, AppointmentSource.BOT));
    }

    /**
     * Porta UNICA de criacao de agendamento de cliente (bot e site): checa o horario na hora de
     * gravar, atribui um barbeiro livre QUE FAZ o servico, grava e cria lembrete/avaliacao.
     * O ponto de fidelidade vem no fechamento da comanda, nao aqui.
     *
     * <p>Regras de negocio por cliente (ex.: limite de agendamentos futuros) entram AQUI, para
     * valerem igual no bot e no site.
     *
     * @return vazio quando o horario nao comporta mais um agendamento (todas as cadeiras ocupadas,
     *         nenhum barbeiro livre faz o servico, barbeiro pedido ocupado com {@code exigirPreferido},
     *         ou bloqueio)
     */
    public Optional<Appointment> agendar(NovoAgendamento req) {
        UUID shop = TenantContext.getCurrent();
        int duracao = req.durationMinutes() != null && req.durationMinutes() > 0
                ? req.durationMinutes()
                : agendaAvailabilityService.duracaoDoServico(req.service());

        Appointment app;
        synchronized (travas.computeIfAbsent(shop, k -> new Object())) {
            AgendaAvailabilityService.Checagem checagem = agendaAvailabilityService.checarHorario(
                    req.date(), req.time(), duracao, req.preferredProfessionalId(), req.exigirPreferido());
            if (!checagem.livre()) {
                log.info("[AGENDA] Horario {} {} recusado para {}: sem cadeira livre pelos {}min do servico.",
                        AppointmentDates.formatDate(req.date()), AppointmentDates.formatTime(req.time()),
                        req.phone(), duracao);
                return Optional.empty();
            }

            UUID professionalId = null;
            if (checagem.temProfissionais()) {
                List<UUID> aptos = professionalAssignmentService.qualifiedIds(checagem.profissionaisLivres(), req.service());
                if (aptos.isEmpty()) {
                    log.info("[AGENDA] Horario {} {} recusado para {}: nenhum barbeiro livre faz '{}'.",
                            AppointmentDates.formatDate(req.date()), AppointmentDates.formatTime(req.time()),
                            req.phone(), req.service());
                    return Optional.empty();
                }
                professionalId = professionalAssignmentService.escolherEntre(aptos, req.date(), req.preferredProfessionalId());
            }

            app = Appointment.builder()
                .barbershopId(shop)
                .customerName(req.name())
                .customerPhone(req.phone())
                .appointmentDate(req.date())
                .appointmentTime(req.time())
                .service(req.service())
                .professionalId(professionalId)
                .paymentStatus(PaymentStatus.PENDENTE)
                .source(req.source() != null ? req.source() : AppointmentSource.BOT)
                .build();
            Appointment saved = appointmentRepository.save(app);
            // save() com mock (testes) devolve null; com JPA devolve a mesma instancia com o id.
            if (saved != null) app = saved;
        }
        if (app.getProfessionalId() != null) {
            log.info("[AGENDA] Profissional {} atribuido ao agendamento de {}", app.getProfessionalId(), req.phone());
        }
        aposGravar(app);
        return Optional.of(app);
    }

    /**
     * Agendamento criado pela recepcao/dono no painel (agenda interativa): o barbeiro e o horario
     * vem escolhidos e ja validados pelo {@link AgendaPanelService} — aqui nao ha auto-atribuicao.
     *
     * <p>Depois de gravar, dispara o mesmo que o bot e o site ({@link #aposGravar}): lembrete e
     * pedido de avaliacao. Origem PAINEL, para o relatorio separar o que a IA marcou.
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
            .source(AppointmentSource.PAINEL)
            .build();
        Appointment saved = appointmentRepository.save(app);
        // save() com mock (testes) devolve null; com JPA devolve a mesma instancia com o id.
        Appointment ag = saved != null ? saved : app;
        aposGravar(ag);
        return ag;
    }

    /** O que nasce com todo agendamento novo: log, lembrete e pedido de avaliacao. */
    private void aposGravar(Appointment ag) {
        log.info("[AGENDA] Novo agendamento ({}) salvo: {} para o dia {} às {}", ag.getSource(), ag.getCustomerName(),
                AppointmentDates.formatDate(ag.getAppointmentDate()), AppointmentDates.formatTime(ag.getAppointmentTime()));

        agendaTaskService.criarTarefasAgendadas(ag);

        // Fidelidade NAO e dada aqui: agendar nao e ser atendido (cancelamento nunca estornava o
        // ponto). O ponto vem no fechamento da comanda — ver FidelityVisitService.
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

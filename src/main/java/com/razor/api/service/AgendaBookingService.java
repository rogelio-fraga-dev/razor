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
import com.razor.api.util.PhoneNumbers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 *   <li><b>Consultar</b>: horarios futuros do cliente e se ele tem horario futuro ativo.</li>
 *   <li><b>Limite</b>: {@link #checkBookingLimit} — quantos horarios futuros um cliente pode ter
 *       ao mesmo tempo (V36). Reaproveitavel por qualquer caminho que crie agendamento.</li>
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
    private final BarbershopSettingsService barbershopSettingsService;
    private final BookingLock bookingLock;
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
     * Cliente que ja tem o maximo de horarios futuros permitido pela barbearia (V36).
     *
     * @param limit    o limite configurado
     * @param upcoming os horarios futuros que ele ja tem, do mais proximo ao mais distante
     */
    public record BookingLimitExceeded(int limit, List<Appointment> upcoming) {

        /**
         * Resposta gentil ao cliente: diz o limite, lista o que ele ja tem e oferece remarcar.
         * Mora aqui (e nao no fluxo do bot) para o agendamento online falar a mesma coisa.
         */
        public String customerMessage(String firstName) {
            String nome = (firstName == null || firstName.isBlank()) ? "" : ", " + firstName.trim();
            StringBuilder sb = new StringBuilder("Poxa").append(nome).append("! 😅 ")
                    .append(limit == 1
                            ? "Por aqui cada cliente pode ter *1 horário* marcado por vez"
                            : "Por aqui cada cliente pode ter até *" + limit + " horários* marcados ao mesmo tempo")
                    .append(", e você já está com ")
                    .append(upcoming.size() == 1 ? "este:" : "estes:")
                    .append("\n\n");
            for (Appointment a : upcoming) {
                sb.append("🔹 ").append(a.getService() == null ? "Atendimento" : a.getService())
                  .append(" — ").append(AppointmentDates.formatDate(a.getAppointmentDate()))
                  .append(" às ").append(AppointmentDates.formatTime(a.getAppointmentTime()))
                  .append("\n");
            }
            sb.append("\nSe quiser trocar algum deles de dia ou horário, é só digitar ")
              .append("*cancelar meu horário* e escolher qual liberar — aí eu marco o novo pra você! 💈");
            return sb.toString();
        }
    }

    /** Lancada por quem tenta criar agendamento passando do limite sem ter conferido antes. */
    public static class BookingLimitExceededException extends RuntimeException {
        private final transient BookingLimitExceeded detail;

        public BookingLimitExceededException(BookingLimitExceeded detail) {
            super("Cliente ja tem " + detail.upcoming().size() + " agendamento(s) futuro(s); o limite da barbearia e "
                    + detail.limit() + ".");
            this.detail = detail;
        }

        public BookingLimitExceeded detail() {
            return detail;
        }
    }

    /**
     * O cliente ainda pode marcar mais um horario? Vazio = pode; presente = ja esta no limite,
     * com os horarios que ele tem (para a resposta listar e oferecer remarcacao).
     *
     * <p>Conta so horarios <b>futuros</b> — historico nao trava ninguem. Recebe a barbearia
     * explicita porque o agendamento online (site publico) nao tem tenant no contexto. Limite
     * nulo, zero ou negativo = sem limite.
     */
    public Optional<BookingLimitExceeded> checkBookingLimit(UUID barbershopId, String phone) {
        Integer limit = barbershopSettingsService.getMaxFutureBookingsPerCustomer(barbershopId);
        if (limit == null || limit <= 0 || phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        List<Appointment> upcoming = upcomingFor(barbershopId, phone);
        return upcoming.size() >= limit
                ? Optional.of(new BookingLimitExceeded(limit, upcoming))
                : Optional.empty();
    }

    /** {@link #checkBookingLimit(UUID, String)} no tenant atual. */
    public Optional<BookingLimitExceeded> checkBookingLimit(String phone) {
        return checkBookingLimit(TenantContext.getCurrent(), phone);
    }

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
        // Sem transacao em volta, um conflito no indice unico (rede de seguranca) e so "horario
        // ocupado" — o bot responde como sempre. Dentro de uma transacao maior (site), ela ja esta
        // marcada para rollback: o conflito sobe e quem chama responde 409.
        boolean transacaoExterna = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            // Checar limite, checar horario e gravar: tudo sob a trava da agenda do tenant, que so
            // e solta no COMMIT (ver BookingLock). Antes o limite era conferido fora da trava e a
            // trava era solta antes do commit — dois pedidos simultaneos passavam juntos.
            return bookingLock.run(shop, () -> gravarSobTrava(shop, req, duracao));
        } catch (BookingConflictException e) {
            if (transacaoExterna) throw e;
            return Optional.empty();
        }
    }

    private Optional<Appointment> gravarSobTrava(UUID shop, NovoAgendamento req, int duracao) {
        // Limite de horarios futuros por cliente (V36): vale para o bot e para o site. O painel
        // (agendarPeloPainel) fica de fora de proposito — ali quem decide e o dono/recepcao.
        checkBookingLimit(shop, req.phone()).ifPresent(excedido -> {
            log.info("[AGENDA] Agendamento recusado para {}: ja tem {} horario(s) futuro(s), limite {}",
                    req.phone(), excedido.upcoming().size(), excedido.limit());
            throw new BookingLimitExceededException(excedido);
        });

        // So barbeiros que FAZEM o servico contam como cadeira livre para este pedido.
        List<UUID> aptos = professionalAssignmentService.qualifiedIds(
                agendaAvailabilityService.profissionaisAtivos(), req.service());
        AgendaAvailabilityService.Checagem checagem = agendaAvailabilityService.checarHorario(
                req.date(), req.time(), duracao, req.preferredProfessionalId(), req.exigirPreferido(),
                aptos == null || aptos.isEmpty() ? null : aptos);
        if (!checagem.livre()) {
            log.info("[AGENDA] Horario {} {} recusado para {}: sem cadeira livre pelos {}min do servico.",
                    AppointmentDates.formatDate(req.date()), AppointmentDates.formatTime(req.time()),
                    req.phone(), duracao);
            return Optional.empty();
        }

        UUID professionalId = null;
        if (checagem.temProfissionais()) {
            List<UUID> livresAptos = professionalAssignmentService.qualifiedIds(checagem.profissionaisLivres(), req.service());
            if (livresAptos.isEmpty()) {
                log.info("[AGENDA] Horario {} {} recusado para {}: nenhum barbeiro livre faz '{}'.",
                        AppointmentDates.formatDate(req.date()), AppointmentDates.formatTime(req.time()),
                        req.phone(), req.service());
                return Optional.empty();
            }
            professionalId = professionalAssignmentService.escolherEntre(livresAptos, req.date(), req.preferredProfessionalId());
        }

        Appointment app = Appointment.builder()
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
        // Quem chama (AgendaPanelService) ja esta dentro do BookingLock: validou o encaixe e grava
        // na mesma trava, que vai ate o commit.
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
     * <p>Nao passa pelo limite de horarios por cliente (V36): quem lanca e o dono, e o limite
     * existe para conter o cliente que reserva "para garantir" — nao para travar a barbearia.
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

    /**
     * Horarios do cliente que ainda vao acontecer, do mais proximo ao mais distante. E isto —
     * e nao o historico inteiro — que o bot chama de "seus agendamentos": listar, cancelar e
     * contar para o limite so faz sentido sobre o que ainda nao aconteceu.
     */
    public List<Appointment> getAgendamentosFuturos(String phone) {
        return upcomingFor(TenantContext.getCurrent(), phone);
    }

    /**
     * O horario futuro <b>marcado por ultimo</b> — o que acabou de ser criado no fluxo do bot.
     * E o alvo da escolha de pagamento e do "desistir" logo depois de agendar; antes era o
     * primeiro registro qualquer do telefone, que podia ser um atendimento de meses atras.
     */
    public Appointment getAgendamentoMaisRecente(String phone) {
        return getAgendamentosFuturos(phone).stream()
                .max(Comparator.comparing(Appointment::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    /**
     * Horarios futuros do telefone em QUALQUER grafia dele (com/sem DDI, com/sem o nono digito):
     * o site grava como o cliente digitou, o bot como o WhatsApp entrega — e e a mesma pessoa.
     */
    public List<Appointment> upcomingFor(UUID barbershopId, String phone) {
        if (phone == null || phone.isBlank()) return List.of();
        LocalDateTime agora = LocalDateTime.now(ZONE);
        List<Appointment> out = appointmentRepository.findUpcomingByCustomer(
                barbershopId, PhoneNumbers.variants(phone), agora.toLocalDate(), agora.toLocalTime());
        return out == null ? List.of() : out;
    }

    /**
     * O cliente tem horario por vir? Mesma regra do limite e do "seus horarios"
     * ({@link #upcomingFor}), consultada no banco — antes lia o historico inteiro do telefone e
     * usava uma regra propria (ate 1h depois do inicio).
     */
    public boolean hasAgendamentoFuturo(String phone) {
        return !upcomingFor(TenantContext.getCurrent(), phone).isEmpty();
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

    /** Cancela o horario futuro marcado por ultimo (ver {@link #getAgendamentoMaisRecente}). */
    public boolean cancelarAgendamentoMaisRecente(String phone) {
        Appointment ag = getAgendamentoMaisRecente(phone);
        if (ag != null) {
            agendaTaskService.cancelarTarefasDoAgendamento(ag);
            appointmentRepository.delete(ag);
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            return true;
        }
        return false;
    }
}

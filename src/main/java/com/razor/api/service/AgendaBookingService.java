package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.Coupon;
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
 *   <li><b>Criar</b>: {@link #adicionarAgendamentoViaIA} (atribui barbeiro, cria lembrete/avaliacao
 *       e registra o ponto de fidelidade) e {@link #processAgenda} (lote em JSON do admin).</li>
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
    private final FidelityService fidelityService;
    private final WhatsAppService whatsAppService;
    private final BarbershopSettingsService barbershopSettingsService;
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
     * Porta UNICA de criacao de agendamento de cliente: checa o horario na hora de gravar, atribui
     * o barbeiro, grava e dispara os efeitos colaterais (lembrete, avaliacao, ponto de fidelidade).
     *
     * <p>Regras de negocio por cliente (ex.: limite de agendamentos futuros) entram AQUI, para
     * valerem igual no bot, no painel e no site.
     *
     * @return vazio quando o horario nao comporta mais um agendamento (todas as cadeiras ocupadas,
     *         barbeiro pedido ocupado com {@code exigirPreferido}, ou bloqueio)
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

            UUID professionalId = checagem.temProfissionais()
                    ? professionalAssignmentService.escolherEntre(
                            checagem.profissionaisLivres(), req.date(), req.preferredProfessionalId())
                    : null;

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
            appointmentRepository.save(app);
        }
        if (app.getProfessionalId() != null) {
            log.info("[AGENDA] Profissional {} atribuido ao agendamento de {}", app.getProfessionalId(), req.phone());
        }

        log.info("[AGENDA] Novo agendamento ({}) salvo: {} para o dia {} às {}", app.getSource(), req.name(),
                AppointmentDates.formatDate(req.date()), AppointmentDates.formatTime(req.time()));

        agendaTaskService.criarTarefasAgendadas(app);

        registrarPontoFidelidade(req.phone());
        return Optional.of(app);
    }

    /**
     * Ponto do cartao fidelidade. Zerar a cartela e emitir o cupom acontecem na mesma
     * transacao dentro do FidelityService: se a emissao falhar, o cliente nao perde os pontos.
     */
    private void registrarPontoFidelidade(String phone) {
        Optional<Coupon> recompensa;
        try {
            recompensa = fidelityService.registerVisit(phone);
        } catch (Exception ex) {
            // Fidelidade nao pode derrubar o agendamento, que ja esta salvo. Mas o erro precisa
            // aparecer inteiro no log: antes so a mensagem era registrada, sem stack trace.
            log.error("[FIDELIDADE] Falha ao registrar ponto de {} — pontos preservados pelo rollback", phone, ex);
            return;
        }

        // O envio fica fora da transacao: e chamada de rede, e falha de rede nao pode desfazer
        // uma recompensa ja conquistada. Se a mensagem nao sair, o cupom continua valido e o
        // codigo fica no log para a barbearia reenviar.
        recompensa.ifPresent(coupon -> {
            try {
                String nomeBarbearia = barbershopSettingsService.resolve().displayName();
                String msg = "🎉 *PARABÉNS!* Você completou "
                        + CouponService.FIDELITY_TARGET_POINTS + " pontos no seu Cartão Fidelidade da "
                        + nomeBarbearia + "! 🥳✂️\n\n"
                        + "Como recompensa, você ganhou *"
                        + coupon.getDiscountValue().stripTrailingZeros().toPlainString()
                        + "% de desconto* no seu próximo corte! Apresente o código abaixo no caixa:\n\n"
                        + "🎟️ *" + coupon.getCode() + "*\n\n"
                        + "_Válido até " + AppointmentDates.formatDate(coupon.getValidUntil()) + "._\n\n"
                        + "Obrigado pela preferência e por fazer parte da nossa história! 👊💈";
                whatsAppService.sendTextMessage(phone, msg);
            } catch (Exception ex) {
                log.error("[FIDELIDADE] Cupom {} foi emitido para {} mas a mensagem nao saiu — "
                        + "informe o codigo ao cliente manualmente", coupon.getCode(), phone, ex);
            }
        });
    }

    /**
     * Lote de agendamentos enviado pelo admin ({@code {"items":[{name,time,date,service}]}}).
     * Retorna quantos foram de clientes conhecidos (com telefone, e portanto com lembrete).
     */
    public int processAgenda(String jsonAgenda) {
        int agendados = 0;
        try {
            JsonNode root = objectMapper.readTree(jsonAgenda);
            JsonNode items = root.get("items");

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
        } catch (Exception e) {
            log.error("Erro Agenda JSON: {}", e.getMessage());
            return 0;
        }
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
            appointmentRepository.delete(ag);
            agendaTaskService.cancelarTarefasClienteExato(ag.getCustomerPhone());
            return true;
        }
        return false;
    }

    public boolean cancelarAgendamentoCliente(String phone) {
        Appointment ag = getAgendamentoCliente(phone);
        if (ag != null) {
            appointmentRepository.delete(ag);
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            agendaTaskService.cancelarTarefasBanco(phone);
            return true;
        }
        return false;
    }
}

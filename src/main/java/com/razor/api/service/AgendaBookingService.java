package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.Appointment;
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
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
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
    private final FidelityService fidelityService;
    private final WhatsAppService whatsAppService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        adicionarAgendamentoViaIA(name, phone, date, time, service, null);
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

    /**
     * Cria um agendamento e atribui um profissional (barbeiro): usa o {@code preferredProfessionalId}
     * se informado e livre; caso contrario auto-atribui o barbeiro livre menos ocupado no dia.
     *
     * @throws BookingLimitExceededException se o cliente ja esta no limite de horarios futuros.
     *         O bot confere antes ({@link #checkBookingLimit}) para responder com jeito; esta
     *         trava e a garantia para quem esquecer de conferir.
     */
    public void adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service,
                                          UUID preferredProfessionalId) {
        LocalDate parsedDate;
        LocalTime parsedTime;
        try {
            parsedDate = AppointmentDates.parseDate(date);
            parsedTime = AppointmentDates.parseTime(time);
        } catch (Exception e) {
            log.error("[AGENDA] Nao foi possivel criar agendamento para {} — data/hora invalida ('{}' '{}'): {}", phone, date, time, e.getMessage());
            return;
        }

        checkBookingLimit(phone).ifPresent(excedido -> {
            log.info("[AGENDA] Agendamento recusado para {}: ja tem {} horario(s) futuro(s), limite {}",
                    phone, excedido.upcoming().size(), excedido.limit());
            throw new BookingLimitExceededException(excedido);
        });

        UUID professionalId = professionalAssignmentService.assign(parsedDate, parsedTime, preferredProfessionalId);

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
        appointmentRepository.save(app);
        if (professionalId != null) {
            log.info("[AGENDA] Profissional {} atribuido ao agendamento de {}", professionalId, phone);
        }

        log.info("[AGENDA] Novo agendamento salvo: {} para o dia {} às {}", name, AppointmentDates.formatDate(parsedDate), AppointmentDates.formatTime(parsedTime));

        agendaTaskService.criarTarefasAgendadas(app);

        registrarPontoFidelidade(phone);
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
     *
     * <p>Nao passa pelo limite de horarios por cliente (V36): quem lanca e o dono, e o limite
     * existe para conter o cliente que reserva "para garantir" — nao para travar a barbearia.
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

    private List<Appointment> upcomingFor(UUID barbershopId, String phone) {
        LocalDateTime agora = LocalDateTime.now(ZONE);
        return appointmentRepository.findUpcomingByCustomer(
                barbershopId, phone, agora.toLocalDate(), agora.toLocalTime());
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

    /** Cancela o horario futuro marcado por ultimo (ver {@link #getAgendamentoMaisRecente}). */
    public boolean cancelarAgendamentoMaisRecente(String phone) {
        Appointment ag = getAgendamentoMaisRecente(phone);
        if (ag != null) {
            appointmentRepository.delete(ag);
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            agendaTaskService.cancelarTarefasBanco(phone);
            return true;
        }
        return false;
    }
}

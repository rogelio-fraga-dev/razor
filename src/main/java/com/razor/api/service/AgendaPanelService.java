package com.razor.api.service;

import com.razor.api.dto.AgendaBoardResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.Comanda;
import com.razor.api.model.Professional;
import com.razor.api.model.ScheduleBlock;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ComandaRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda interativa do painel: a grade (dia/semana) e as acoes da recepcao sobre ela — criar,
 * remarcar (arrastar), trocar de barbeiro, cancelar e marcar comparecimento.
 *
 * <p><b>Validacao no servidor, sempre.</b> O painel faz a troca otimista na tela, mas quem decide
 * e aqui: dia aberto, dentro do expediente, sem folga/bloqueio, sem sobrepor outro horario do
 * barbeiro (considerando a duracao do servico) e barbeiro ativo. Falha vira
 * {@link IllegalArgumentException} (400 com mensagem legivel) e o painel desfaz a troca.
 *
 * <p><b>Cliente avisado.</b> Remarcar ou cancelar pelo painel manda WhatsApp para o cliente (a
 * mesma camada de envio do bot) e o lembrete acompanha o novo horario: as tarefas do horario
 * antigo sao canceladas e as do novo criadas ({@link AgendaTaskService}).
 *
 * <p>Criar delega a gravacao ao {@link AgendaBookingService}, para que regras de criacao (lembrete,
 * fidelidade, limites futuros) valham igual para IA e painel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaPanelService {

    /** Intervalo maximo de uma consulta da grade — a visao semanal usa 7. */
    static final int MAX_RANGE_DAYS = 31;
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ComandaRepository comandaRepository;
    private final ScheduleBlockService scheduleBlockService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final PricingService pricingService;
    private final CustomerService customerService;
    private final AgendaBookingService bookingService;
    private final AgendaTaskService taskService;
    private final WhatsAppService whatsAppService;

    // ------------------------------------------------------------------ leitura

    public AgendaBoardResponse board(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Informe o período.");
        if (to.isBefore(from)) throw new IllegalArgumentException("O fim do período vem antes do início.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Período máximo da agenda: " + MAX_RANGE_DAYS + " dias.");
        }
        UUID shop = TenantContext.getCurrent();
        var cfg = barbershopSettingsService.resolve();

        List<Appointment> apps = appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(shop, from, to);
        List<Professional> pros = professionalRepository.findByBarbershopId(shop);
        Map<UUID, Professional> prosById = pros.stream()
                .collect(Collectors.toMap(Professional::getId, Function.identity(), (a, b) -> a));
        Map<PricingService.ServiceCategory, Integer> durations = pricingService.resolveDurations();
        Set<String> subscribers = customerService.subscriberPhones();
        Map<Long, Comanda> comandas = comandasOf(shop, apps);

        // Colunas: ativos + inativos que ainda tem horario no periodo (senao o bloco sumiria).
        Set<UUID> comAgendamento = apps.stream().map(Appointment::getProfessionalId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<AgendaBoardResponse.Pro> columns = pros.stream()
                .filter(p -> p.isActive() || comAgendamento.contains(p.getId()))
                .sorted(Comparator.comparing((Professional p) -> !p.isActive())
                        .thenComparing(p -> p.getName() == null ? "" : p.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(p -> new AgendaBoardResponse.Pro(p.getId(), p.getName(), p.isActive(), photoUrlOf(p)))
                .toList();

        List<AgendaBoardResponse.Item> items = apps.stream()
                .sorted(Comparator.comparing(Appointment::getAppointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Appointment::getAppointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> {
                    Professional p = a.getProfessionalId() == null ? null : prosById.get(a.getProfessionalId());
                    Comanda c = a.getId() == null ? null : comandas.get(a.getId());
                    return new AgendaBoardResponse.Item(
                            a.getId(), a.getAppointmentDate(), a.getAppointmentTime(),
                            durationOf(a.getService(), durations),
                            a.getCustomerName(), a.getCustomerPhone(), a.getService(),
                            a.getProfessionalId(), p != null ? p.getName() : null,
                            a.getPaymentStatus(), a.getAttendanceStatus(),
                            a.getCustomerPhone() != null && subscribers.contains(a.getCustomerPhone()),
                            c != null ? c.getId() : null,
                            c != null && c.getStatus() != null ? c.getStatus().name() : null,
                            a.getConfirmationStatus(), a.getDepositStatus());
                })
                .toList();

        List<ScheduleBlock> allBlocks = scheduleBlockService.listAll();
        List<AgendaBoardResponse.Block> blocks = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (ScheduleBlock b : allBlocks) {
                if (b.appliesOn(d)) {
                    blocks.add(new AgendaBoardResponse.Block(b.getId(), b.getProfessionalId(), d,
                            b.getStartTime(), b.getEndTime(), b.getReason()));
                }
            }
        }

        List<Integer> openDays = cfg.workingWeekdays().stream().map(DayOfWeek::getValue).sorted().toList();
        return new AgendaBoardResponse(from, to, cfg.openingTime(), cfg.closingTime(), openDays, columns, items, blocks);
    }

    // ------------------------------------------------------------------ escrita

    /** Cria um agendamento escolhido na grade (barbeiro e horario fixos, sem auto-atribuicao). */
    @Transactional
    public Appointment create(String customerName, String rawPhone, UUID serviceId, UUID professionalId,
                              LocalDate date, LocalTime time, boolean notify) {
        String name = customerName == null ? "" : customerName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Informe o nome do cliente.");
        String phone = normalizePhone(rawPhone);
        ServiceOffering service = requireService(serviceId);
        int duration = durationOf(service.getName(), pricingService.resolveDurations());

        validateSlot(date, time, duration, professionalId, null);

        // Cliente novo entra na base (ex.: ligou na barbearia em vez de chamar no WhatsApp).
        customerService.findOrCreateCustomer(phone, name);
        Appointment saved = bookingService.agendarPeloPainel(name, phone, date, time, service.getName(), professionalId);

        if (notify) {
            send(saved, "Olá, " + firstName(saved) + "! 💈 Seu horário na *" + shopName() + "* está confirmado: *"
                    + when(date, time) + "*, " + service.getName() + " com *" + professionalName(professionalId)
                    + "*.\n\nSe precisar remarcar, é só responder aqui.");
        }
        return saved;
    }

    /**
     * Remarca para outra data/hora e/ou outro barbeiro. As tarefas do horario antigo sao
     * canceladas ANTES de mudar o agendamento e as do novo criadas depois: o lembrete acompanha.
     */
    @Transactional
    public Appointment move(Long id, LocalDate date, LocalTime time, UUID professionalId, boolean notify) {
        Appointment ag = require(id);
        if (ag.getAttendanceStatus() == AttendanceStatus.ATTENDED) {
            throw new IllegalArgumentException("Este atendimento já foi realizado — não dá para remarcar.");
        }
        boolean sameSlot = date.equals(ag.getAppointmentDate()) && time.equals(ag.getAppointmentTime());
        boolean sameBarber = Objects.equals(professionalId, ag.getProfessionalId());
        if (sameSlot && sameBarber) return ag; // soltou no mesmo lugar: nada a fazer, nada a avisar

        int duration = durationOf(ag.getService(), pricingService.resolveDurations());
        validateSlot(date, time, duration, professionalId, ag.getId());

        taskService.cancelarTarefasDoAgendamento(ag); // ainda no horario antigo
        ag.setAppointmentDate(date);
        ag.setAppointmentTime(time);
        ag.setProfessionalId(professionalId);
        ag.setAttendanceStatus(null); // remarcou: o que valia para o horario antigo nao vale mais
        ag.setConfirmationStatus(null); // a confirmacao era do horario antigo; o novo pergunta de novo
        ag.setNotified(false);
        Appointment saved = appointmentRepository.save(ag);
        Appointment result = saved != null ? saved : ag;
        taskService.criarTarefasAgendadas(result);

        log.info("[AGENDA] Agendamento {} remarcado pelo painel para {} {} (barbeiro {})", id, date, time, professionalId);
        if (notify) {
            String msg = sameSlot
                    ? "Olá, " + firstName(result) + "! 💈 Seu horário de *" + when(date, time) + "* na *" + shopName()
                            + "* continua o mesmo, mas agora você será atendido por *" + professionalName(professionalId) + "*."
                    : "Olá, " + firstName(result) + "! 💈 Seu horário na *" + shopName() + "* foi *remarcado* para *"
                            + when(date, time) + "* com *" + professionalName(professionalId) + "*."
                            + "\n\nSe não puder nesse horário, é só responder aqui.";
            send(result, msg);
        }
        return result;
    }

    /** Cancela pelo painel: mesmo caminho do bot (apaga e cancela as tarefas DESTE horario). */
    @Transactional
    public void cancel(Long id, boolean notify) {
        Appointment ag = require(id);
        LocalDate date = ag.getAppointmentDate();
        LocalTime time = ag.getAppointmentTime();
        bookingService.cancelarAgendamentoExato(ag);
        log.info("[AGENDA] Agendamento {} cancelado pelo painel", id);

        // Horario que ja passou nao precisa de aviso ("seu corte de ontem foi cancelado" so confunde).
        boolean futuro = date != null && time != null
                && LocalDateTime.of(date, time).isAfter(LocalDateTime.now(AppointmentDates.ZONE));
        if (notify && futuro) {
            send(ag, "Olá, " + firstName(ag) + ". Seu agendamento de *" + when(date, time) + "* na *" + shopName()
                    + "* foi *cancelado* pela barbearia.\n\nQuer marcar outro horário? É só responder aqui. 💈");
        }
    }

    /**
     * Marca se o cliente veio. So depois que o horario comecou — "faltou" num horario futuro nao
     * existe. Falta cancela o pedido de avaliacao pendente (nao se pede nota de quem nao veio).
     */
    @Transactional
    public Appointment setAttendance(Long id, AttendanceStatus status) {
        Appointment ag = require(id);
        if (status != null && ag.getAppointmentDate() != null && ag.getAppointmentTime() != null
                && LocalDateTime.of(ag.getAppointmentDate(), ag.getAppointmentTime())
                        .isAfter(LocalDateTime.now(AppointmentDates.ZONE))) {
            throw new IllegalArgumentException("O horário ainda não começou — marque o comparecimento depois.");
        }
        ag.setAttendanceStatus(status);
        if (status == AttendanceStatus.NO_SHOW) {
            taskService.cancelarTarefasDoAgendamento(ag);
        }
        Appointment saved = appointmentRepository.save(ag);
        return saved != null ? saved : ag;
    }

    /**
     * Dono/recepcao confirma que o sinal (V42) foi recebido. O sistema nao confere o PIX sozinho:
     * quem ve o extrato e a barbearia.
     */
    @Transactional
    public Appointment confirmDeposit(Long id) {
        Appointment ag = require(id);
        if (ag.getDepositStatus() == null) {
            throw new IllegalArgumentException("Este agendamento não tem sinal pendente.");
        }
        ag.setDepositStatus(com.razor.api.model.DepositStatus.PAID);
        Appointment saved = appointmentRepository.save(ag);
        return saved != null ? saved : ag;
    }

    // ------------------------------------------------------------------ validacao

    /**
     * Regras do encaixe de um horario de {@code duration} minutos para o barbeiro. Lanca
     * {@link IllegalArgumentException} com a mensagem que o painel mostra no toast.
     *
     * <p>Restricao "barbeiro atende este servico": ainda nao existe no cadastro do profissional;
     * quando existir, entra aqui.
     */
    void validateSlot(LocalDate date, LocalTime time, int duration, UUID professionalId, Long ignoreId) {
        if (date == null || time == null) throw new IllegalArgumentException("Informe data e horário.");
        if (professionalId == null) throw new IllegalArgumentException("Escolha o barbeiro.");
        UUID shop = TenantContext.getCurrent();

        Professional pro = professionalRepository.findByBarbershopId(shop).stream()
                .filter(p -> professionalId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Barbeiro não encontrado."));
        if (!pro.isActive()) {
            throw new IllegalArgumentException(pro.getName() + " está inativo — escolha outro barbeiro.");
        }

        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(AppointmentDates.ZONE))) {
            throw new IllegalArgumentException("Esse horário já passou.");
        }

        var cfg = barbershopSettingsService.resolve();
        if (!cfg.isOpenOn(date.getDayOfWeek())) {
            throw new IllegalArgumentException("A barbearia não abre "
                    + date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR) + " (" + cfg.workingDaysText() + ").");
        }
        LocalTime open = parseTime(cfg.openingTime(), LocalTime.of(9, 0));
        LocalTime close = parseTime(cfg.closingTime(), LocalTime.of(19, 0));
        LocalTime end = time.plusMinutes(duration);
        // end < time = virou a meia-noite: certamente fora do expediente.
        if (time.isBefore(open) || end.isAfter(close) || end.isBefore(time)) {
            throw new IllegalArgumentException("Fora do expediente (" + AppointmentDates.formatTime(open) + "–"
                    + AppointmentDates.formatTime(close) + "): o serviço dura " + duration + " min.");
        }

        if (scheduleBlockService.isBlocked(date, time, duration, professionalId)) {
            throw new IllegalArgumentException("Horário bloqueado para " + pro.getName() + " (folga, almoço ou feriado).");
        }

        Map<PricingService.ServiceCategory, Integer> durations = pricingService.resolveDurations();
        for (Appointment other : appointmentRepository
                .findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, professionalId, date)) {
            if (other.getAppointmentTime() == null) continue;
            if (ignoreId != null && ignoreId.equals(other.getId())) continue;
            if (other.getAttendanceStatus() == AttendanceStatus.NO_SHOW) continue; // cadeira livre
            LocalTime os = other.getAppointmentTime();
            LocalTime oe = os.plusMinutes(durationOf(other.getService(), durations));
            if (time.isBefore(oe) && os.isBefore(end)) {
                throw new IllegalArgumentException(pro.getName() + " já tem " + nameOr(other)
                        + " às " + AppointmentDates.formatTime(os) + " — escolha outro horário.");
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Duracao pelo catalogo (por categoria, a mesma regra da disponibilidade do bot). */
    int durationOf(String service, Map<PricingService.ServiceCategory, Integer> durations) {
        Integer mins = durations.get(pricingService.categorize(service));
        return mins != null && mins > 0 ? mins : 30;
    }

    /**
     * Telefone digitado no painel para o formato do WhatsApp (DDI 55 + DDD + numero, so
     * digitos) — o mesmo que chega do webhook, para o cliente nao virar dois cadastros.
     */
    static String normalizePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10 || digits.length() == 11) digits = "55" + digits;
        if (digits.length() < 12 || digits.length() > 13) {
            throw new IllegalArgumentException("Telefone inválido — use DDD + número (ex.: 11 99999-0000).");
        }
        return digits;
    }

    private Appointment require(Long id) {
        UUID shop = TenantContext.getCurrent();
        return appointmentRepository.findById(id)
                .filter(a -> shop.equals(a.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Agendamento não encontrado."));
    }

    private ServiceOffering requireService(UUID serviceId) {
        UUID shop = TenantContext.getCurrent();
        ServiceOffering svc = serviceOfferingRepository.findById(serviceId)
                .filter(s -> shop.equals(s.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Serviço não encontrado."));
        if (!svc.isActive()) throw new IllegalArgumentException("O serviço \"" + svc.getName() + "\" está inativo.");
        return svc;
    }

    private Map<Long, Comanda> comandasOf(UUID shop, List<Appointment> apps) {
        List<Long> ids = apps.stream().map(Appointment::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Comanda> out = new HashMap<>();
        for (Comanda c : comandaRepository.findByBarbershopIdAndAppointmentIdIn(shop, ids)) {
            // Prefere a comanda que nao foi cancelada, se houver mais de uma.
            out.merge(c.getAppointmentId(), c, (a, b) -> a.getStatus() == Comanda.Status.CANCELADA ? b : a);
        }
        return out;
    }

    /** Foto do profissional (data URI do perfil completo, V37); sem foto, a agenda mostra as iniciais. */
    private static String photoUrlOf(Professional p) {
        String v = p.getPhotoData();
        return v != null && !v.isBlank() ? v : null;
    }

    private void send(Appointment ag, String message) {
        String phone = ag.getCustomerPhone();
        if (phone == null || phone.isBlank() || "N/A".equals(phone)) return;
        try {
            whatsAppService.sendTextMessage(phone, message);
        } catch (Exception e) {
            // A mudanca ja esta salva; a falha de envio nao pode desfaze-la.
            log.error("[AGENDA] Aviso ao cliente {} nao saiu: {}", phone, e.getMessage());
        }
    }

    private String shopName() {
        return barbershopSettingsService.resolve().displayName();
    }

    private String professionalName(UUID id) {
        if (id == null) return "nossa equipe";
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .filter(p -> id.equals(p.getId())).map(Professional::getName).findFirst().orElse("nossa equipe");
    }

    private static String firstName(Appointment ag) {
        String n = ag.getCustomerName();
        if (n == null || n.isBlank()) return "tudo bem";
        return n.trim().split("\\s+")[0];
    }

    private static String nameOr(Appointment a) {
        String n = a.getCustomerName();
        return n == null || n.isBlank() ? "um cliente" : n.trim().split("\\s+")[0];
    }

    /** "sexta, 20/11 às 14:30". */
    private static String when(LocalDate date, LocalTime time) {
        String dia = date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
        return dia + ", " + String.format("%02d/%02d", date.getDayOfMonth(), date.getMonthValue())
                + " às " + AppointmentDates.formatTime(time);
    }

    private static LocalTime parseTime(String hhmm, LocalTime fallback) {
        if (hhmm == null || hhmm.isBlank()) return fallback;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

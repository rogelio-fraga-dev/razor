package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.AutomationSend;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.Coupon;
import com.razor.api.model.Customer;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.AutomationSendRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.PhoneNumbers;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Campanhas automaticas (V47): "sumiu ha N dias", aniversario com cupom e lembrete de retorno do
 * corte. O scheduler chama {@link #runInBackground} a cada rodada; aqui se decide QUEM recebe
 * ({@link #plan}, funcao pura) e se envia ({@link #deliver}).
 *
 * <p>Regras (todas valem para as tres campanhas):
 * <ul>
 *   <li><b>Horario comercial</b>: so com a barbearia aberta (dia de funcionamento, entre a abertura
 *       e o fechamento).</li>
 *   <li><b>Limite de frequencia</b>: no maximo 1 mensagem automatica de marketing por cliente a
 *       cada N dias (padrao 7), contando as tres campanhas.</li>
 *   <li><b>Opt-out</b>: quem respondeu "parar" nunca recebe. Admin, cliente em atendimento humano
 *       (pausado) e telefone invalido tambem nao.</li>
 *   <li><b>Idempotencia</b>: cada ocasiao (ultima visita, ano do aniversario, atendimento de
 *       origem) vira uma linha em {@code automation_sends} com chave unica — nunca sai duas vezes,
 *       nem depois de restart. Falha de envio e contada e tentada de novo ate
 *       {@link AutomationSend#MAX_ATTEMPTS} vezes.</li>
 *   <li><b>Ritmo</b>: no maximo {@value #MAX_PER_RUN} por barbearia por rodada, com a mesma pausa
 *       anti-bloqueio do disparo em massa, no executor de disparos.</li>
 * </ul>
 */
@Slf4j
@Service
public class AutomationCampaignService {

    static final int MAX_PER_RUN = 25;
    /** Aniversario cai num dia fechado: ainda vale nos proximos dias. */
    static final int BIRTHDAY_GRACE_DAYS = 3;
    /** Lembrete de retorno passou do ponto ha mais que isto: ja e caso do "sumiu". */
    static final int RETURN_GRACE_DAYS = 7;
    /** "Sumiu" so ate este tanto alem do limite — quem sumiu ha anos nao e mais "sumido", e outra base. */
    static final int LAPSED_WINDOW_DAYS = 90;
    /** Historico lido para achar a ultima visita. */
    static final int HISTORY_DAYS = 400;

    private static final Set<AutomationSend.Kind> MARKETING =
            EnumSet.of(AutomationSend.Kind.LAPSED, AutomationSend.Kind.BIRTHDAY, AutomationSend.Kind.RETURN);

    private final AutomationSettingsService settingsService;
    private final AutomationSendRepository sendRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final TenantAdminService tenantAdminService;
    private final CustomerService customerService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final CouponService couponService;
    private final WhatsAppMessenger messenger;
    private final long delayMillis;
    /** Uma rodada por barbearia de cada vez (a anterior pode ainda estar na pausa entre envios). */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public AutomationCampaignService(AutomationSettingsService settingsService, AutomationSendRepository sendRepository,
                                     CustomerRepository customerRepository, AppointmentRepository appointmentRepository,
                                     ServiceOfferingRepository serviceOfferingRepository,
                                     TenantAdminService tenantAdminService, CustomerService customerService,
                                     BarbershopSettingsService barbershopSettingsService, CouponService couponService,
                                     WhatsAppMessenger messenger,
                                     @Value("${razor.broadcast.delay-ms:3000}") long delayMillis) {
        this.settingsService = settingsService;
        this.sendRepository = sendRepository;
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.tenantAdminService = tenantAdminService;
        this.customerService = customerService;
        this.barbershopSettingsService = barbershopSettingsService;
        this.couponService = couponService;
        this.messenger = messenger;
        this.delayMillis = delayMillis;
    }

    /** Um envio decidido: tipo, cliente, ocasiao (chave de idempotencia) e as variaveis do texto. */
    public record Candidate(AutomationSend.Kind kind, Customer customer, String referenceKey, Map<String, String> vars) {
    }

    /** Tudo que o planejamento precisa, ja carregado. */
    record PlanInput(AutomationSettings cfg, List<Customer> customers, List<Appointment> appointments,
                     Map<String, Integer> returnDaysByService, LocalDate today, LocalTime nowTime,
                     Set<String> settledKeys, Set<String> cappedPhones, Set<String> excludedPhones) {
    }

    // ------------------------------------------------------------------ rodada

    /** Rodada da barbearia em segundo plano (o tenant vem explicito: o @Async nao o herda). */
    @Async(com.razor.api.config.AsyncConfig.BROADCAST_EXECUTOR)
    public void runInBackground(UUID barbershopId) {
        if (!running.add(barbershopId)) return;
        TenantContext.set(barbershopId);
        try {
            run(LocalDateTime.now(AppointmentDates.ZONE));
        } catch (Exception e) {
            log.error("[AUTOMACOES] Rodada de campanhas falhou no tenant {}", barbershopId, e);
        } finally {
            TenantContext.clear();
            running.remove(barbershopId);
        }
    }

    /** Planeja e envia no tenant atual. Devolve quantas mensagens sairam. */
    public int run(LocalDateTime now) {
        UUID shop = TenantContext.getCurrent();
        AutomationSettings cfg = settingsService.getFor(shop);
        if (!cfg.anyCampaignEnabled()) return 0;
        var bot = barbershopSettingsService.resolve();
        if (!withinBusinessHours(now, bot.workingWeekdays(), parse(bot.openingTime(), LocalTime.of(9, 0)),
                parse(bot.closingTime(), LocalTime.of(19, 0)))) {
            return 0;
        }

        List<Customer> customers = customerRepository.findByBarbershopId(shop);
        if (customers == null || customers.isEmpty()) return 0;
        LocalDate today = now.toLocalDate();
        List<Appointment> appts = appointmentRepository
                .findByBarbershopIdAndAppointmentDateGreaterThanEqual(shop, today.minusDays(HISTORY_DAYS));

        Map<String, Integer> returnDays = new HashMap<>();
        for (ServiceOffering o : serviceOfferingRepository.findByBarbershopId(shop)) {
            if (o.getReturnReminderDays() != null) returnDays.put(normalize(o.getName()), o.getReturnReminderDays());
        }

        Set<String> settled = new HashSet<>();
        for (AutomationSend s : sendRepository.findByBarbershopIdAndKindIn(shop, MARKETING)) {
            if (s.isSettled()) settled.add(key(s.getKind(), s.getCustomerPhone(), s.getReferenceKey()));
        }
        Set<String> capped = new HashSet<>(sendRepository.phonesSentSince(shop, MARKETING,
                now.minusDays(cfg.getFrequencyCapDays())));
        Set<String> excluded = new HashSet<>();
        for (Customer c : customers) {
            String p = c.getPhoneNumber();
            if (p != null && (tenantAdminService.isAdmin(p) || customerService.isCustomerPaused(p))) excluded.add(p);
        }

        List<Candidate> plano = plan(new PlanInput(cfg, customers, appts, returnDays, today, now.toLocalTime(),
                settled, capped, excluded));
        if (plano.isEmpty()) return 0;
        log.info("[AUTOMACOES] {} mensagem(ns) de campanha planejadas no tenant {}", plano.size(), shop);
        return deliver(cfg, plano, now);
    }

    // ------------------------------------------------------------------ planejamento (puro)

    static List<Candidate> plan(PlanInput in) {
        AutomationSettings cfg = in.cfg();
        // Visitas por telefone (chave de comparacao: sem DDI e sem o nono digito).
        Map<String, List<Appointment>> porTelefone = new HashMap<>();
        for (Appointment a : in.appointments()) {
            if (a.getCustomerPhone() == null || a.getAppointmentDate() == null) continue;
            porTelefone.computeIfAbsent(PhoneNumbers.normalizeBrazilian(a.getCustomerPhone()), k -> new ArrayList<>()).add(a);
        }

        List<Candidate> out = new ArrayList<>();
        for (Customer c : in.customers()) {
            String phone = c.getPhoneNumber();
            if (!elegivel(c, in)) continue;
            List<Appointment> visitas = porTelefone.getOrDefault(PhoneNumbers.normalizeBrazilian(phone), List.of());
            boolean temFuturo = visitas.stream().anyMatch(a -> isFuture(a, in.today(), in.nowTime()));
            Appointment ultima = visitas.stream()
                    .filter(a -> !isFuture(a, in.today(), in.nowTime()))
                    .filter(a -> a.getAttendanceStatus() != AttendanceStatus.NO_SHOW)
                    .max(Comparator.comparing(Appointment::getAppointmentDate)
                            .thenComparing(a -> a.getAppointmentTime() == null ? LocalTime.MIN : a.getAppointmentTime()))
                    .orElse(null);

            Candidate escolhido = null;
            if (cfg.isBirthdayEnabled()) escolhido = birthday(c, in);
            if (escolhido == null && cfg.isReturnEnabled() && !temFuturo) escolhido = returnReminder(c, ultima, in);
            if (escolhido == null && cfg.isLapsedEnabled() && !temFuturo) escolhido = lapsed(c, ultima, in);
            if (escolhido != null) out.add(escolhido);
        }
        // Aniversario primeiro (tem dia certo); o resto na ordem da base. Teto por rodada.
        out.sort(Comparator.comparing(cand -> cand.kind() == AutomationSend.Kind.BIRTHDAY ? 0 : 1));
        return out.size() > MAX_PER_RUN ? new ArrayList<>(out.subList(0, MAX_PER_RUN)) : out;
    }

    private static boolean elegivel(Customer c, PlanInput in) {
        String phone = c.getPhoneNumber();
        if (phone == null || phone.replaceAll("\\D", "").length() < 10) return false;
        if (c.isMarketingOptedOut()) return false;
        if (in.excludedPhones().contains(phone)) return false;
        return !in.cappedPhones().contains(phone);
    }

    private static Candidate birthday(Customer c, PlanInput in) {
        if (c.getBirthDate() == null) return null;
        MonthDay md = MonthDay.from(c.getBirthDate());
        int ano = in.today().getYear();
        // 29/02 em ano nao bissexto: comemora no dia 28.
        LocalDate niver = md.isValidYear(ano) ? md.atYear(ano) : LocalDate.of(ano, 2, 28);
        if (in.today().isBefore(niver) || in.today().isAfter(niver.plusDays(BIRTHDAY_GRACE_DAYS))) return null;
        String ref = String.valueOf(ano);
        if (in.settledKeys().contains(key(AutomationSend.Kind.BIRTHDAY, c.getPhoneNumber(), ref))) return null;
        return new Candidate(AutomationSend.Kind.BIRTHDAY, c, ref, baseVars(c));
    }

    private static Candidate returnReminder(Customer c, Appointment ultima, PlanInput in) {
        if (ultima == null || ultima.getService() == null) return null;
        Integer dias = in.returnDaysByService().get(normalize(ultima.getService()));
        if (dias == null) return null;
        LocalDate vence = ultima.getAppointmentDate().plusDays(dias);
        if (in.today().isBefore(vence) || in.today().isAfter(vence.plusDays(RETURN_GRACE_DAYS))) return null;
        String ref = ultima.getId() != null ? "ag-" + ultima.getId() : "d-" + ultima.getAppointmentDate();
        if (in.settledKeys().contains(key(AutomationSend.Kind.RETURN, c.getPhoneNumber(), ref))) return null;
        Map<String, String> vars = baseVars(c);
        vars.put("dias", String.valueOf(ChronoUnit.DAYS.between(ultima.getAppointmentDate(), in.today())));
        vars.put("servico", ultima.getService().toLowerCase(java.util.Locale.ROOT));
        return new Candidate(AutomationSend.Kind.RETURN, c, ref, vars);
    }

    private static Candidate lapsed(Customer c, Appointment ultima, PlanInput in) {
        LocalDate ultimaVisita = ultima != null ? ultima.getAppointmentDate() : null;
        if (c.getLastServiceDate() != null) {
            LocalDate cadastro = c.getLastServiceDate().toLocalDate();
            if (ultimaVisita == null || cadastro.isAfter(ultimaVisita)) ultimaVisita = cadastro;
        }
        if (ultimaVisita == null) return null; // nunca veio: nao "sumiu"
        long dias = ChronoUnit.DAYS.between(ultimaVisita, in.today());
        int limite = in.cfg().getLapsedDays();
        if (dias < limite || dias > limite + LAPSED_WINDOW_DAYS) return null;
        // Uma vez por periodo de ausencia: a ocasiao e a data da ultima visita.
        String ref = ultimaVisita.toString();
        if (in.settledKeys().contains(key(AutomationSend.Kind.LAPSED, c.getPhoneNumber(), ref))) return null;
        Map<String, String> vars = baseVars(c);
        vars.put("dias", String.valueOf(dias));
        return new Candidate(AutomationSend.Kind.LAPSED, c, ref, vars);
    }

    private static Map<String, String> baseVars(Customer c) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("nome", AutomationTemplates.firstName(c.getName()));
        return vars;
    }

    static boolean isFuture(Appointment a, LocalDate today, LocalTime now) {
        if (a.getAppointmentDate().isAfter(today)) return true;
        if (a.getAppointmentDate().isBefore(today)) return false;
        return a.getAppointmentTime() == null || !a.getAppointmentTime().isBefore(now);
    }

    /** Aberta agora: dia de funcionamento e entre a abertura e o fechamento. */
    public static boolean withinBusinessHours(LocalDateTime now, Set<DayOfWeek> openDays, LocalTime open, LocalTime close) {
        if (openDays == null || !openDays.contains(now.getDayOfWeek())) return false;
        LocalTime t = now.toLocalTime();
        return !t.isBefore(open) && t.isBefore(close);
    }

    static String key(AutomationSend.Kind kind, String phone, String ref) {
        return kind + "|" + phone + "|" + ref;
    }

    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static LocalTime parse(String hhmm, LocalTime fallback) {
        try {
            return hhmm == null ? fallback : LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ envio

    /**
     * Envia o plano, um por vez, com a pausa anti-bloqueio. Envio SINCRONO (so conta como enviado o
     * que a Evolution aceitou), cada um registrado antes de sair (PENDING) e fechado como SENT ou
     * FAILED — contagem de falhas como no disparo em massa.
     */
    int deliver(AutomationSettings cfg, List<Candidate> plano, LocalDateTime now) {
        UUID shop = TenantContext.getCurrent();
        var bot = barbershopSettingsService.resolve();
        int enviados = 0;
        int falhas = 0;
        for (Candidate cand : plano) {
            String phone = cand.customer().getPhoneNumber();
            AutomationSend row = claim(shop, cand);
            if (row == null) continue;
            try {
                Map<String, String> vars = new LinkedHashMap<>(cand.vars());
                vars.put("barbearia", bot.displayName());
                vars.put("link", bot.scheduleUrl());
                String template = switch (cand.kind()) {
                    case LAPSED -> cfg.getLapsedTemplate();
                    case BIRTHDAY -> cfg.getBirthdayTemplate();
                    case RETURN -> cfg.getReturnTemplate();
                    case WEEKLY_REPORT -> null;
                };
                if (cand.kind() == AutomationSend.Kind.BIRTHDAY) {
                    birthdayVars(cfg, row, phone, vars, now.toLocalDate());
                }
                String texto = AutomationTemplates.render(AutomationTemplates.effective(template, cand.kind()), vars);
                messenger.sendSync(phone, texto, "AUTOMACAO");
                row.setStatus(AutomationSend.Status.SENT);
                row.setSentAt(LocalDateTime.now(AppointmentDates.ZONE));
                row.setLastError(null);
                sendRepository.save(row);
                enviados++;
                if (delayMillis > 0) Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                row.setStatus(AutomationSend.Status.FAILED);
                sendRepository.save(row);
                break;
            } catch (Exception e) {
                falhas++;
                row.setStatus(AutomationSend.Status.FAILED);
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                row.setLastError(msg.length() > 250 ? msg.substring(0, 250) : msg);
                sendRepository.save(row);
                log.warn("[AUTOMACOES] Falha ao enviar {} para {}: {}", cand.kind(), phone, msg);
            }
        }
        log.info("[AUTOMACOES] Rodada no tenant {}: {} enviadas, {} falhas", shop, enviados, falhas);
        return enviados;
    }

    /**
     * Reserva a ocasiao: linha nova PENDING, ou a FAILED de uma tentativa anterior. Nula = ja
     * resolvida (enviada, em envio, falhou vezes demais) ou outra rodada pegou primeiro.
     */
    private AutomationSend claim(UUID shop, Candidate cand) {
        String phone = cand.customer().getPhoneNumber();
        try {
            AutomationSend row = sendRepository
                    .findByBarbershopIdAndKindAndCustomerPhoneAndReferenceKey(shop, cand.kind(), phone, cand.referenceKey())
                    .orElse(null);
            if (row != null && row.isSettled()) return null;
            if (row == null) {
                row = AutomationSend.builder()
                        .barbershopId(shop)
                        .kind(cand.kind())
                        .customerPhone(phone)
                        .referenceKey(cand.referenceKey())
                        .build();
            }
            row.setStatus(AutomationSend.Status.PENDING);
            row.setAttempts(row.getAttempts() + 1);
            AutomationSend saved = sendRepository.save(row);
            return saved != null ? saved : row;
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            return null;
        }
    }

    /** Cupom do aniversario: emitido uma vez por ocasiao (nova tentativa reaproveita o codigo). */
    private void birthdayVars(AutomationSettings cfg, AutomationSend row, String phone, Map<String, String> vars,
                              LocalDate today) {
        String code = row.getDetail();
        if (code == null) {
            Coupon cupom = couponService.issueBirthdayCoupon(phone, cfg.getBirthdayDiscountPercent(),
                    cfg.getBirthdayCouponValidDays());
            code = cupom.getCode();
            row.setDetail(code);
            sendRepository.save(row);
        }
        fillBirthdayVars(vars, code, cfg.getBirthdayDiscountPercent(), today.plusDays(cfg.getBirthdayCouponValidDays()));
    }

    static void fillBirthdayVars(Map<String, String> vars, String code, BigDecimal percent, LocalDate validUntil) {
        vars.put("cupom", code);
        vars.put("desconto", percent.stripTrailingZeros().toPlainString() + "%");
        vars.put("validade", AppointmentDates.formatDate(validUntil));
    }

    // ------------------------------------------------------------------ previa

    /** Previa no painel: o texto com um cliente de exemplo e os dados reais da barbearia. */
    public String preview(AutomationSend.Kind kind, String template) {
        AutomationSettings cfg = settingsService.get();
        var bot = barbershopSettingsService.resolve();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("nome", "João");
        vars.put("barbearia", bot.displayName());
        vars.put("link", bot.scheduleUrl());
        switch (kind) {
            case LAPSED -> vars.put("dias", String.valueOf(cfg.getLapsedDays()));
            case RETURN -> {
                vars.put("dias", "21");
                vars.put("servico", "corte");
            }
            case BIRTHDAY -> fillBirthdayVars(vars, "NIVER-AB12C", cfg.getBirthdayDiscountPercent(),
                    AppointmentDates.today().plusDays(cfg.getBirthdayCouponValidDays()));
            default -> { }
        }
        return AutomationTemplates.render(AutomationTemplates.effective(template, kind), vars);
    }
}

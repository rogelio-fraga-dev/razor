package com.razor.api.service;

import com.razor.api.dto.AiImpactResponse;
import com.razor.api.model.AiImpactEvent;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import com.razor.api.model.Interaction;
import com.razor.api.repository.AiImpactEventRepository;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Card "O que a IA fez por voce" (V42). Definicoes — todas medidas, nenhuma estimada:
 *
 * <ul>
 *   <li><b>Agendamentos por origem</b>: agendamentos <b>criados</b> no periodo ({@code created_at}),
 *       separados por {@code source}. "Este mes a IA marcou 87" = o que ela marcou este mes, mesmo
 *       que o corte seja no mes que vem. Faltas ({@code NO_SHOW}) ficam de fora — nao viraram
 *       atendimento. Cancelados nao existem mais (cancelar apaga). O valor e o preco de tabela,
 *       com a mesma regra do {@link ReportService} ({@link PricingService}).</li>
 *   <li><b>Mensagens respondidas</b>: interacoes {@code BOT} no periodo. <b>Fora do expediente</b>:
 *       em dia que a barbearia nao abre, ou antes da abertura / a partir do fechamento.</li>
 *   <li><b>Clientes resgatados</b>: clientes distintos que marcaram <b>pelo bot</b> no periodo e,
 *       no dia em que marcaram, estavam {@code SUMIDO} pela {@link CustomerStatusRules} (calculada
 *       so com as visitas anteriores aquele dia).</li>
 *   <li><b>Anti-falta</b>: eventos de {@code ai_impact_events} no periodo (confirmacoes,
 *       desmarcacoes pela confirmacao, liberacoes automaticas, vagas reaproveitadas pela lista de
 *       espera). Faltas evitadas = desmarcacoes pela confirmacao.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AiImpactService {

    private final AppointmentRepository appointmentRepository;
    private final InteractionRepository interactionRepository;
    private final CustomerRepository customerRepository;
    private final AiImpactEventRepository eventRepository;
    private final PricingService pricingService;
    private final BarbershopSettingsService barbershopSettingsService;

    public enum Period { MONTH, LAST_30_DAYS }

    /** Intervalo do periodo: mes corrente ate hoje, ou os ultimos 30 dias (hoje incluso). */
    public static LocalDate[] range(Period period, LocalDate today) {
        return period == Period.LAST_30_DAYS
                ? new LocalDate[] {today.minusDays(29), today}
                : new LocalDate[] {today.withDayOfMonth(1), today};
    }

    public AiImpactResponse impact(LocalDate from, LocalDate to) {
        UUID shop = TenantContext.getCurrent();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // --- agendamentos por origem
        // Calculo unico do preco do agendamento (soma do catalogo, combo quando houver) — o mesmo
        // do bot e do faturamento. Antes: preco por "categoria" (5 servicos saiam como R$ 35).
        PricingService.Quoter precos = pricingService.quoter();
        Map<AppointmentSource, long[]> counts = new EnumMap<>(AppointmentSource.class);
        Map<AppointmentSource, BigDecimal> revenue = new EnumMap<>(AppointmentSource.class);
        List<Appointment> botBookings = new ArrayList<>();
        for (Appointment a : appointmentRepository
                .findByBarbershopIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(shop, start, end)) {
            if (a.getAttendanceStatus() == AttendanceStatus.NO_SHOW) continue;
            AppointmentSource src = a.getSource() != null ? a.getSource() : AppointmentSource.BOT;
            counts.computeIfAbsent(src, k -> new long[1])[0]++;
            BigDecimal preco = precos.price(a.getService());
            revenue.merge(src, preco != null ? preco : BigDecimal.ZERO, BigDecimal::add);
            if (src == AppointmentSource.BOT) botBookings.add(a);
        }

        // --- mensagens
        List<LocalDateTime> msgs = interactionRepository.findTimestamps(shop, Interaction.InteractionType.BOT, start, end);
        BarbershopSettingsService.BotSettings cfg = barbershopSettingsService.resolve();
        LocalTime abre = parseTime(cfg.openingTime(), LocalTime.of(9, 0));
        LocalTime fecha = parseTime(cfg.closingTime(), LocalTime.of(19, 0));
        long foraDoHorario = msgs.stream()
                .filter(t -> t != null && isOutsideHours(t, cfg.workingWeekdays(), abre, fecha))
                .count();

        long confirmed = events(shop, AiImpactEvent.Type.CONFIRMED, start, end);
        long declined = events(shop, AiImpactEvent.Type.DECLINED, start, end);
        long autoReleased = events(shop, AiImpactEvent.Type.AUTO_RELEASED, start, end);
        long refills = events(shop, AiImpactEvent.Type.WAITLIST_REFILL, start, end);

        return new AiImpactResponse(from, to,
                channel(counts, revenue, AppointmentSource.BOT),
                channel(counts, revenue, AppointmentSource.SITE),
                channel(counts, revenue, AppointmentSource.PAINEL),
                msgs.size(), foraDoHorario,
                rescued(shop, botBookings),
                confirmed, declined, autoReleased, refills, declined);
    }

    /** Fora do expediente: dia fechado, antes de abrir ou a partir do fechamento. */
    public static boolean isOutsideHours(LocalDateTime t, Set<DayOfWeek> openDays, LocalTime opening, LocalTime closing) {
        if (openDays == null || !openDays.contains(t.getDayOfWeek())) return true;
        LocalTime h = t.toLocalTime();
        return h.isBefore(opening) || !h.isBefore(closing);
    }

    private long rescued(UUID shop, List<Appointment> botBookings) {
        if (botBookings.isEmpty()) return 0;
        Map<String, Customer> porTelefone = new HashMap<>();
        for (Customer c : customerRepository.findByBarbershopId(shop)) {
            if (c.getPhoneNumber() != null) porTelefone.putIfAbsent(c.getPhoneNumber(), c);
        }
        // Dia em que marcou -> telefones. Ordenado para que o resgate conte uma vez por cliente.
        Map<LocalDate, Set<String>> porDia = new TreeMap<>();
        for (Appointment a : botBookings) {
            if (a.getCreatedAt() == null || a.getCustomerPhone() == null) continue;
            if (!porTelefone.containsKey(a.getCustomerPhone())) continue;
            porDia.computeIfAbsent(a.getCreatedAt().toLocalDate(), k -> new HashSet<>()).add(a.getCustomerPhone());
        }
        if (porDia.isEmpty()) return 0;

        List<Appointment> todos = appointmentRepository.findByBarbershopId(shop);
        Set<String> resgatados = new HashSet<>();
        for (var dia : porDia.entrySet()) {
            LocalDate d = dia.getKey();
            // So o que aconteceu ANTES daquele dia: o proprio agendamento novo e as visitas depois
            // dele nao podem tirar o cliente do "sumido".
            List<Appointment> anteriores = todos.stream()
                    .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isBefore(d))
                    .toList();
            List<Customer> sombras = new ArrayList<>();
            for (String phone : dia.getValue()) {
                if (resgatados.contains(phone)) continue;
                Customer c = porTelefone.get(phone);
                LocalDateTime ultimo = c.getLastServiceDate();
                sombras.add(Customer.builder()
                        .id(c.getId() != null ? c.getId() : UUID.randomUUID())
                        .phoneNumber(phone)
                        .createdAt(c.getCreatedAt())
                        .lastServiceDate(ultimo != null && ultimo.toLocalDate().isBefore(d) ? ultimo : null)
                        .build());
            }
            if (sombras.isEmpty()) continue;
            var snap = CustomerStatusRules.classify(sombras, anteriores, d);
            for (Customer s : sombras) {
                var cls = snap.byCustomer().get(s.getId());
                if (cls != null && cls.status() == CustomerStatus.SUMIDO) resgatados.add(s.getPhoneNumber());
            }
        }
        return resgatados.size();
    }

    private long events(UUID shop, AiImpactEvent.Type type, LocalDateTime start, LocalDateTime end) {
        return eventRepository.countByBarbershopIdAndEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                shop, type, start, end);
    }

    private static AiImpactResponse.Channel channel(Map<AppointmentSource, long[]> counts,
                                                    Map<AppointmentSource, BigDecimal> revenue, AppointmentSource src) {
        long n = counts.containsKey(src) ? counts.get(src)[0] : 0;
        return new AiImpactResponse.Channel(n, revenue.getOrDefault(src, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private static LocalTime parseTime(String hhmm, LocalTime fallback) {
        try {
            return hhmm == null ? fallback : LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

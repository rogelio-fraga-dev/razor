package com.razor.api.service;

import com.razor.api.dto.OccupancyResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.model.ScheduleBlock;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Taxa de ocupacao das cadeiras: horas vendidas sobre horas disponiveis.
 *
 * <p>E a metrica que responde "vale a pena contratar mais um barbeiro?" — faturamento subindo com
 * ocupacao baixa significa que ha cadeira sobrando; ocupacao alta com faturamento parado significa
 * que o problema e preco, nao volume.
 *
 * <p>So passou a ser calculavel com a V32: antes o sistema nao sabia em que <b>dias</b> a
 * barbearia abre, e contar domingo como disponivel derrubaria a taxa em cerca de 1/7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancyService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ScheduleBlockService scheduleBlockService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final PricingService pricingService;

    /** Duracao assumida quando o servico nao casa com nenhuma categoria do catalogo. */
    private static final int FALLBACK_MINUTES = 30;

    public OccupancyResponse occupancy(LocalDate from, LocalDate to) {
        UUID shop = TenantContext.getCurrent();
        var cfg = barbershopSettingsService.resolve();

        LocalTime opening = parseTime(cfg.openingTime(), LocalTime.of(9, 0));
        LocalTime closing = parseTime(cfg.closingTime(), LocalTime.of(19, 0));
        long journeyMinutes = Math.max(0, Duration.between(opening, closing).toMinutes());

        List<LocalDate> openDays = openDaysBetween(from, to, cfg);
        List<Professional> actives = professionalRepository.findByBarbershopIdAndActiveTrue(shop);
        List<ScheduleBlock> blocks = scheduleBlockService.listAll();

        Map<UUID, Integer> bookedMinutes = new HashMap<>();
        Map<UUID, Integer> appointmentCount = new HashMap<>();
        int unassigned = 0;

        PricingService.Quoter durations = pricingService.quoter();
        for (Appointment a : appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(shop, from, to)) {
            int minutes = durations.duration(a.getService());
            if (minutes <= 0) minutes = FALLBACK_MINUTES;
            if (a.getProfessionalId() == null) {
                // Ocupa cadeira de verdade, mas nao da para dizer de quem. Distribuir por chute
                // inventaria ocupacao para quem talvez nao tenha atendido.
                unassigned++;
                continue;
            }
            bookedMinutes.merge(a.getProfessionalId(), minutes, Integer::sum);
            appointmentCount.merge(a.getProfessionalId(), 1, Integer::sum);
        }

        List<OccupancyResponse.ProfessionalOccupancy> perProfessional = new ArrayList<>();
        long totalAvailable = 0;
        long totalBooked = 0;

        for (Professional p : actives) {
            long available = 0;
            for (LocalDate day : openDays) {
                available += journeyMinutes - blockedMinutesOn(blocks, day, p.getId(), opening, closing);
            }
            available = Math.max(0, available);
            int booked = bookedMinutes.getOrDefault(p.getId(), 0);

            totalAvailable += available;
            totalBooked += booked;

            perProfessional.add(new OccupancyResponse.ProfessionalOccupancy(
                    p.getId(), p.getName(), hours(available), hours(booked), rate(booked, available),
                    appointmentCount.getOrDefault(p.getId(), 0)));
        }

        // Maior ocupacao primeiro: a pergunta e quem esta no limite e quem tem folga.
        perProfessional.sort((a, b) -> {
            BigDecimal ra = a.rate() == null ? BigDecimal.valueOf(-1) : a.rate();
            BigDecimal rb = b.rate() == null ? BigDecimal.valueOf(-1) : b.rate();
            return rb.compareTo(ra);
        });

        return new OccupancyResponse(from, to, openDays.size(),
                hours(totalAvailable), hours(totalBooked), rate(totalBooked, totalAvailable),
                unassigned, perProfessional);
    }

    /** Dias do intervalo em que a barbearia abre (V32). */
    private List<LocalDate> openDaysBetween(LocalDate from, LocalDate to,
                                            BarbershopSettingsService.BotSettings cfg) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (cfg.isOpenOn(d.getDayOfWeek())) days.add(d);
        }
        return days;
    }

    /**
     * Minutos da jornada que estao bloqueados para este barbeiro neste dia (folga, almoco,
     * feriado — V16). Bloqueio de dia inteiro zera a jornada; faixa desconta so a interseccao,
     * porque um almoco das 12h as 13h nao tira o dia todo.
     *
     * <p>Bloqueios sobrepostos podem descontar duas vezes — aceito: a alternativa seria unir
     * intervalos, e na pratica ninguem cadastra dois almocos no mesmo horario. O
     * {@code Math.max(0, ...)} no chamador impede que isso vire hora disponivel negativa.
     */
    private long blockedMinutesOn(List<ScheduleBlock> blocks, LocalDate day, UUID professionalId,
                                  LocalTime opening, LocalTime closing) {
        long blocked = 0;
        for (ScheduleBlock b : blocks) {
            if (!b.appliesTo(professionalId) || !b.appliesOn(day)) continue;

            if (b.getStartTime() == null || b.getEndTime() == null) {
                return Duration.between(opening, closing).toMinutes(); // dia inteiro
            }
            LocalTime start = b.getStartTime().isBefore(opening) ? opening : b.getStartTime();
            LocalTime end = b.getEndTime().isAfter(closing) ? closing : b.getEndTime();
            if (start.isBefore(end)) blocked += Duration.between(start, end).toMinutes();
        }
        return blocked;
    }

    /** Minutos -> horas com uma casa decimal. */
    private BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** Percentual, ou nulo quando nao houve hora disponivel — ver a nota no OccupancyResponse. */
    private BigDecimal rate(long booked, long available) {
        if (available <= 0) return null;
        return BigDecimal.valueOf(booked)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(available), 1, RoundingMode.HALF_UP);
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(raw);
        } catch (Exception e) {
            log.debug("[OCUPACAO] Horario '{}' invalido, usando {}.", raw, fallback);
            return fallback;
        }
    }
}

package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Disponibilidade da agenda em slots de 30min, no formato de texto que vai para o prompt da IA.
 *
 * <p>Considera: expediente e dias de funcionamento do tenant (Configuracoes &gt; Funcionamento),
 * duracao do servico no catalogo (um combo de 60min ocupa dois slots), bloqueios de agenda
 * (folga/feriado/almoco), horarios ja passados de hoje e a capacidade em cadeiras (barbeiros
 * ativos). Somente leitura: nao cria nem altera agendamentos. Extraido do antigo
 * {@link AgendaService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaAvailabilityService {

    private static final ZoneId ZONE = AppointmentDates.ZONE;
    static final String NENHUM_HORARIO = "Nenhum horário disponível para este dia.";
    private static final String AGENDA_LIVRE = "Agenda totalmente livre. Todos os horários estão disponíveis.";

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PricingService pricingService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final ScheduleBlockService scheduleBlockService;

    /** Horarios em que TODAS as cadeiras estao ocupadas, agrupados por data (contexto da IA). */
    public String getHorariosOcupadosParaIA() {
        UUID shop = TenantContext.getCurrent();
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(shop);
        if (agendaAtiva.isEmpty()) return AGENDA_LIVRE;

        // Capacidade = nº de barbeiros ativos (cadeiras). Na agenda multiprofissional um
        // horário só fica INDISPONÍVEL quando TODAS as cadeiras estão ocupadas nele.
        int capacidade = Math.max(1, professionalRepository.findByBarbershopIdAndActiveTrue(shop).size());

        // Conta agendamentos por (data, hora); TreeMap ordena os horários (HH:mm).
        Map<String, Map<String, Integer>> porDataHora = new LinkedHashMap<>();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) continue;
            String d = AppointmentDates.formatDate(ag.getAppointmentDate());
            String h = AppointmentDates.formatTime(ag.getAppointmentTime());
            porDataHora.computeIfAbsent(d, k -> new TreeMap<>()).merge(h, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder("Horários INDISPONÍVEIS por data (todas as " + capacidade + " cadeiras ocupadas):\n");
        boolean algum = false;
        for (Map.Entry<String, Map<String, Integer>> entry : porDataHora.entrySet()) {
            List<String> cheios = new ArrayList<>();
            for (Map.Entry<String, Integer> he : entry.getValue().entrySet()) {
                if (he.getValue() >= capacidade) cheios.add(he.getKey());
            }
            if (!cheios.isEmpty()) {
                algum = true;
                sb.append("  - ").append(entry.getKey()).append(": ").append(String.join(", ", cheios)).append("\n");
            }
        }
        if (!algum) return AGENDA_LIVRE;
        return sb.toString().trim();
    }

    /** Ate 4 horarios livres do dia ({@code dd/MM/yyyy} ou "hoje"/"amanha"), olhando a barbearia toda. */
    public String getHorariosDisponiveisDoDia(String date) {
        LocalDate target;
        try {
            target = AppointmentDates.parseDate(date);
        } catch (Exception e) {
            return NENHUM_HORARIO;
        }

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        List<Appointment> doDia = agendaAtiva.stream()
                .filter(a -> target.equals(a.getAppointmentDate()) && a.getAppointmentTime() != null)
                .collect(Collectors.toList());
        return montarHorariosLivres(target, expandirSlotsOcupados(doDia), null);
    }

    /**
     * Horarios disponiveis de UM profissional (agenda multiprofissional): a ocupacao
     * considera apenas os agendamentos daquele barbeiro, entao dois profissionais podem
     * ter o mesmo horario livre de forma independente.
     */
    public String getHorariosDisponiveisDoProfissional(UUID professionalId, String date) {
        LocalDate target;
        try {
            target = AppointmentDates.parseDate(date);
        } catch (Exception e) {
            return NENHUM_HORARIO;
        }

        List<Appointment> apps = appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(
                TenantContext.getCurrent(), professionalId, target);
        return montarHorariosLivres(target, expandirSlotsOcupados(apps), professionalId);
    }

    /**
     * Expande cada agendamento nos slots de 30min que ele realmente ocupa, conforme a duracao do
     * servico (do catalogo): um combo de 60min ocupa dois slots consecutivos, e nao apenas o inicial.
     */
    private List<String> expandirSlotsOcupados(List<Appointment> apps) {
        Map<PricingService.ServiceCategory, Integer> duracoes = pricingService.resolveDurations();
        List<String> ocupados = new ArrayList<>();
        for (Appointment ag : apps) {
            LocalTime inicio = ag.getAppointmentTime();
            if (inicio == null) continue;
            Integer mins = duracoes.get(pricingService.categorize(ag.getService()));
            int slots = Math.max(1, (int) Math.ceil((mins != null ? mins : 30) / 30.0));
            for (int i = 0; i < slots; i++) {
                ocupados.add(AppointmentDates.formatTime(inicio.plusMinutes(30L * i)));
            }
        }
        return ocupados;
    }

    /**
     * Gera ate 4 horarios livres (espacados) dentro do expediente CONFIGURADO pelo tenant
     * (Configuracoes > Funcionamento), excluindo ocupados e passados.
     */
    private String montarHorariosLivres(LocalDate target, List<String> ocupadosDoDia, UUID professionalId) {
        List<String> livres = new ArrayList<>();
        var cfg = barbershopSettingsService.resolve();

        // Dia em que a barbearia nao abre (V32). Ate aqui o expediente so tinha hora de abrir e
        // fechar, e nada olhava o DIA: sem um bloqueio recorrente cadastrado a mao, o bot
        // oferecia e marcava no domingo enquanto dizia ao cliente que abre de seg a sab.
        if (!cfg.isOpenOn(target.getDayOfWeek())) {
            log.debug("[AGENDA] {} cai em {} e a barbearia nao abre nesse dia ({}).",
                    target, target.getDayOfWeek(), cfg.workingDaysText());
            return NENHUM_HORARIO;
        }

        LocalTime inicio = parseHora(cfg.openingTime(), LocalTime.of(9, 0));
        LocalTime fim = parseHora(cfg.closingTime(), LocalTime.of(19, 0));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime cursor = inicio;

        // Verifica se a data consultada é hoje
        boolean isHoje = target.equals(AppointmentDates.today());
        LocalTime agora = LocalTime.now(ZONE);

        while (!cursor.isAfter(fim.minusMinutes(30))) {
            String horario = cursor.format(fmt);

            boolean isPassado = isHoje && cursor.isBefore(agora);
            // Folga/feriado/almoço: nunca oferecer horário bloqueado (V16).
            boolean isBloqueado = scheduleBlockService.isBlocked(target, cursor, 30, professionalId);

            if (!ocupadosDoDia.contains(horario) && !isPassado && !isBloqueado) {
                livres.add(horario);
            }
            cursor = cursor.plusMinutes(30);
        }
        if (livres.isEmpty()) return NENHUM_HORARIO;

        // Limita a 4 opções espaçadas para evitar flood
        if (livres.size() > 4) {
            List<String> spread = new ArrayList<>();
            spread.add(livres.get(0)); // Primeiro livre
            spread.add(livres.get(livres.size() / 3)); // 1/3 do dia livre
            spread.add(livres.get(livres.size() * 2 / 3)); // 2/3 do dia livre
            spread.add(livres.get(livres.size() - 1)); // Último livre
            livres = spread.stream().distinct().limit(4).collect(Collectors.toList());
        }

        return String.join(", ", livres);
    }

    /** "HH:mm" configurado pelo dono; valor invalido/ausente cai no padrao historico. */
    private LocalTime parseHora(String hhmm, LocalTime fallback) {
        if (hhmm == null || hhmm.isBlank()) return fallback;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            log.warn("[AGENDA] Horario de funcionamento invalido ('{}') — usando {}.", hhmm, fallback);
            return fallback;
        }
    }
}

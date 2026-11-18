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
        // So de hoje em diante: o passado nao ocupa cadeira e a tabela inteira crescia sem fim.
        List<Appointment> agendaAtiva = appointmentRepository
                .findByBarbershopIdAndAppointmentDateGreaterThanEqual(shop, AppointmentDates.today());
        if (agendaAtiva == null || agendaAtiva.isEmpty()) return AGENDA_LIVRE;

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

        // Consulta do dia (indice barbershop_id, appointment_date) — antes lia o tenant inteiro.
        List<Appointment> doDiaBruto = appointmentRepository.findByBarbershopIdAndAppointmentDate(
                TenantContext.getCurrent(), target);
        List<Appointment> doDia = (doDiaBruto == null ? List.<Appointment>of() : doDiaBruto).stream()
                .filter(a -> a.getAppointmentTime() != null)
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

    // ------------------------------------------------------------------------------------------
    // Texto da IA quando o servico pedido e conhecido
    // ------------------------------------------------------------------------------------------

    /**
     * Ate 4 horarios livres do dia para o {@code servico} (nome do catalogo), contando so os
     * barbeiros {@code aptos} a ele. Usa a MESMA regra da gravacao ({@link #horariosLivres} →
     * {@link #checarHorario}): duracao do servico, capacidade, bloqueios por barbeiro — o bot nao
     * oferece horario em que so esta livre quem nao faz o servico, e que seria recusado ao gravar.
     * {@code aptos} nulo ou vazio = servico desconhecido: cai no {@link #getHorariosDisponiveisDoDia(String)}.
     */
    public String getHorariosDisponiveisDoDia(String date, String servico, java.util.Collection<UUID> aptos) {
        if (servico == null || servico.isBlank() || aptos == null || aptos.isEmpty()) {
            return getHorariosDisponiveisDoDia(date);
        }
        LocalDate target;
        try {
            target = AppointmentDates.parseDate(date);
        } catch (Exception e) {
            return NENHUM_HORARIO;
        }
        List<String> livres = horariosLivres(target, duracaoDoServico(servico), null, aptos).stream()
                .map(AppointmentDates::formatTime)
                .toList();
        if (livres.isEmpty()) return NENHUM_HORARIO;
        return String.join(", ", espalhar(livres));
    }

    /**
     * {@link #getHorariosOcupadosParaIA()} para um servico conhecido: lista, por data, os horarios
     * (perto dos agendamentos existentes) em que NENHUM barbeiro apto ao servico esta livre pela
     * duracao inteira. Sem isto o "status da agenda" so marcava ocupado quando TODAS as cadeiras
     * estavam cheias — inclusive as de quem nao faz o servico.
     * {@code aptos} nulo ou vazio = servico desconhecido: cai no texto geral.
     */
    public String getHorariosOcupadosParaIA(String servico, java.util.Collection<UUID> aptos) {
        if (servico == null || servico.isBlank() || aptos == null || aptos.isEmpty()) {
            return getHorariosOcupadosParaIA();
        }
        UUID shop = TenantContext.getCurrent();
        LocalDate hoje = AppointmentDates.today();
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopIdAndAppointmentDateGreaterThanEqual(shop, hoje);
        if (agendaAtiva == null || agendaAtiva.isEmpty()) return AGENDA_LIVRE;

        int duracao = Math.max(SLOT_MINUTES, duracaoDoServico(servico));
        java.util.SortedSet<LocalDate> datas = agendaAtiva.stream()
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentTime() != null)
                .map(Appointment::getAppointmentDate)
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        StringBuilder sb = new StringBuilder("Horários INDISPONÍVEIS para " + servico
                + " por data (nenhum barbeiro que faz esse serviço livre):\n");
        boolean algum = false;
        for (LocalDate data : datas) {
            Ocupacao dia = ocupacaoDoDia(data);
            // Candidatos: todo inicio cujo intervalo [t, t+duracao) encosta em algum agendamento
            // do dia. Fora disso a cadeira esta vazia e o horario nao entra na lista.
            java.util.SortedSet<Integer> candidatos = new java.util.TreeSet<>();
            for (Intervalo i : dia.agendamentos()) {
                for (int t = i.inicio() - duracao + SLOT_MINUTES; t < i.fim(); t += SLOT_MINUTES) {
                    if (t >= 0 && t + duracao <= 24 * 60) candidatos.add(t);
                }
            }
            List<String> cheios = new ArrayList<>();
            for (int t : candidatos) {
                if (!avaliar(dia, data, t, duracao, null, false, aptos).livre()) {
                    cheios.add(AppointmentDates.formatTime(LocalTime.of(t / 60, t % 60)));
                }
            }
            if (!cheios.isEmpty()) {
                algum = true;
                sb.append("  - ").append(AppointmentDates.formatDate(data)).append(": ")
                        .append(String.join(", ", cheios)).append("\n");
            }
        }
        if (!algum) return AGENDA_LIVRE;
        return sb.toString().trim();
    }

    /** Corta a lista em ate 4 opcoes espacadas (primeiro, 1/3, 2/3, ultimo) — sem flood no WhatsApp. */
    private static List<String> espalhar(List<String> livres) {
        if (livres.size() <= 4) return livres;
        List<String> spread = new ArrayList<>();
        spread.add(livres.get(0));
        spread.add(livres.get(livres.size() / 3));
        spread.add(livres.get(livres.size() * 2 / 3));
        spread.add(livres.get(livres.size() - 1));
        return spread.stream().distinct().limit(4).collect(Collectors.toList());
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
        return String.join(", ", espalhar(livres));
    }

    // ------------------------------------------------------------------------------------------
    // Grade completa (agendamento online) e checagem na hora de gravar
    // ------------------------------------------------------------------------------------------

    /** Passo da grade, em minutos. O mesmo dos horarios que o bot oferece. */
    static final int SLOT_MINUTES = 30;

    /**
     * Resultado da checagem de um horario: se cabe e quais barbeiros estao livres nele pela
     * duracao inteira do servico.
     *
     * @param livre               o horario aceita mais um agendamento
     * @param temProfissionais    a barbearia tem barbeiros ativos (agenda multiprofissional)
     * @param profissionaisLivres barbeiros ativos livres e sem bloqueio no intervalo inteiro
     */
    public record Checagem(boolean livre, boolean temProfissionais, List<UUID> profissionaisLivres) {
    }

    /**
     * TODOS os horarios livres do dia para um servico de {@code durationMinutes}, sem o corte em 4
     * opcoes que o texto da IA faz. E a grade da pagina publica de agendamento.
     *
     * <p>Mesmas regras do bot: dia de funcionamento, expediente, horarios ja passados, bloqueios e
     * capacidade em cadeiras. A diferenca e que aqui a DURACAO do servico pedido conta — um combo
     * de 60min nao e oferecido as 18:30 se a casa fecha as 19:00, nem num horario cuja segunda
     * meia hora ja esta ocupada.
     *
     * @param professionalId barbeiro escolhido, ou {@code null} para "sem preferencia" (basta um
     *                       barbeiro livre no intervalo inteiro)
     */
    public List<LocalTime> horariosLivres(LocalDate date, int durationMinutes, UUID professionalId) {
        return horariosLivres(date, durationMinutes, professionalId, null);
    }

    /**
     * {@link #horariosLivres(LocalDate, int, UUID)} so com barbeiros que FAZEM o servico.
     *
     * @param aptos barbeiros que atendem o servico pedido (ver
     *              {@link ProfessionalAssignmentService#qualifiedIds}); {@code null} = todos. Sem
     *              isto a grade oferecia um horario em que so estava livre quem nao faz o servico,
     *              e o agendamento era recusado na hora de gravar.
     */
    public List<LocalTime> horariosLivres(LocalDate date, int durationMinutes, UUID professionalId,
                                          java.util.Collection<UUID> aptos) {
        if (date == null) return List.of();
        LocalDate hoje = AppointmentDates.today();
        if (date.isBefore(hoje)) return List.of();

        var cfg = barbershopSettingsService.resolve();
        if (!cfg.isOpenOn(date.getDayOfWeek())) return List.of();

        int abre = minutos(parseHora(cfg.openingTime(), LocalTime.of(9, 0)));
        int fecha = minutos(parseHora(cfg.closingTime(), LocalTime.of(19, 0)));
        int duracao = Math.max(SLOT_MINUTES, durationMinutes);
        int agora = date.equals(hoje) ? minutos(LocalTime.now(ZONE)) : -1;

        Ocupacao dia = ocupacaoDoDia(date);
        List<LocalTime> livres = new ArrayList<>();
        for (int inicio = abre; inicio + duracao <= fecha; inicio += SLOT_MINUTES) {
            if (inicio < agora) continue;
            if (avaliar(dia, date, inicio, duracao, professionalId, true, aptos).livre()) {
                livres.add(LocalTime.of(inicio / 60, inicio % 60));
            }
        }
        return livres;
    }

    /**
     * Checa UM horario na hora de gravar — bot, painel ou site. Ignora expediente e passado (quem
     * chama ja ofereceu um horario valido; o admin pode encaixar fora do expediente) e olha so o
     * que causa overbooking: ocupacao, bloqueios e capacidade.
     *
     * @param preferredProfessionalId barbeiro pedido, ou {@code null}
     * @param exigirPreferido         {@code true} = so vale se o pedido estiver livre (site, onde o
     *                                cliente escolheu na grade dele); {@code false} = outro barbeiro
     *                                livre serve (o bot auto-atribui)
     */
    public Checagem checarHorario(LocalDate date, LocalTime time, int durationMinutes,
                                  UUID preferredProfessionalId, boolean exigirPreferido) {
        return checarHorario(date, time, durationMinutes, preferredProfessionalId, exigirPreferido, null);
    }

    /** {@link #checarHorario} considerando so os barbeiros {@code aptos} ao servico ({@code null} = todos). */
    public Checagem checarHorario(LocalDate date, LocalTime time, int durationMinutes,
                                  UUID preferredProfessionalId, boolean exigirPreferido,
                                  java.util.Collection<UUID> aptos) {
        if (date == null || time == null) return new Checagem(false, false, List.of());
        int duracao = Math.max(SLOT_MINUTES, durationMinutes);
        return avaliar(ocupacaoDoDia(date), date, minutos(time), duracao,
                preferredProfessionalId, exigirPreferido, aptos);
    }

    /** Ids dos barbeiros ativos do tenant atual. */
    public List<UUID> profissionaisAtivos() {
        List<com.razor.api.model.Professional> ativos =
                professionalRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
        return ativos == null ? List.of() : ativos.stream().map(com.razor.api.model.Professional::getId).toList();
    }

    /** Duracao (minutos) de um servico pelo nome, pela mesma tabela que a grade usa. */
    public int duracaoDoServico(String serviceName) {
        Integer mins = pricingService.resolveDurations().get(pricingService.categorize(serviceName));
        return mins != null ? mins : SLOT_MINUTES;
    }

    /** Agendamentos do dia como intervalos [inicio, fim) em minutos, e os barbeiros ativos. */
    private record Ocupacao(List<Intervalo> agendamentos, List<UUID> ativos) {
    }

    private record Intervalo(UUID professionalId, int inicio, int fim) {
        boolean sobrepoe(int ini, int end) {
            return inicio < end && ini < fim;
        }
    }

    private Ocupacao ocupacaoDoDia(LocalDate date) {
        UUID shop = TenantContext.getCurrent();
        Map<PricingService.ServiceCategory, Integer> duracoes = pricingService.resolveDurations();
        List<Intervalo> intervalos = new ArrayList<>();
        List<Appointment> doDia = appointmentRepository.findByBarbershopIdAndAppointmentDate(shop, date);
        for (Appointment ag : doDia == null ? List.<Appointment>of() : doDia) {
            if (ag.getAppointmentTime() == null) continue;
            Integer mins = duracoes == null ? null : duracoes.get(pricingService.categorize(ag.getService()));
            int ini = minutos(ag.getAppointmentTime());
            intervalos.add(new Intervalo(ag.getProfessionalId(), ini,
                    ini + Math.max(SLOT_MINUTES, mins != null ? mins : SLOT_MINUTES)));
        }
        List<UUID> ativos = professionalRepository.findByBarbershopIdAndActiveTrue(shop).stream()
                .map(com.razor.api.model.Professional::getId)
                .toList();
        return new Ocupacao(intervalos, ativos);
    }

    /**
     * O coracao da regra. Um intervalo [inicio, inicio+duracao) cabe quando:
     * <ol>
     *   <li>em cada meia hora dele, o total de agendamentos (atribuidos ou nao) fica abaixo da
     *       capacidade (barbeiros ativos, minimo 1) — agendamento sem barbeiro tambem ocupa
     *       cadeira;</li>
     *   <li>com barbeiros ativos: existe barbeiro sem agendamento sobreposto e sem bloqueio no
     *       intervalo (o pedido, quando {@code exigirPreferido});</li>
     *   <li>sem barbeiros: nenhum bloqueio geral da barbearia incide.</li>
     * </ol>
     */
    private Checagem avaliar(Ocupacao dia, LocalDate date, int inicio, int duracao,
                             UUID preferido, boolean exigirPreferido, java.util.Collection<UUID> aptos) {
        int fim = inicio + duracao;
        int capacidade = Math.max(1, dia.ativos().size());
        for (int t = inicio; t < fim; t += SLOT_MINUTES) {
            int slotIni = t;
            int slotFim = Math.min(fim, t + SLOT_MINUTES);
            long ocupados = dia.agendamentos().stream().filter(i -> i.sobrepoe(slotIni, slotFim)).count();
            if (ocupados >= capacidade) return new Checagem(false, !dia.ativos().isEmpty(), List.of());
        }

        // Horario que vira o dia (expediente ate 23:59) nao e LocalTime valido: nao cabe.
        if (fim > 24 * 60) return new Checagem(false, !dia.ativos().isEmpty(), List.of());
        LocalTime horaInicio = LocalTime.of(inicio / 60, inicio % 60);

        if (dia.ativos().isEmpty()) {
            boolean bloqueado = scheduleBlockService.isBlocked(date, horaInicio, duracao, null);
            return new Checagem(!bloqueado, false, List.of());
        }

        List<UUID> candidatos = ((preferido != null && exigirPreferido)
                ? dia.ativos().stream().filter(preferido::equals).toList()
                : dia.ativos()).stream()
                // Servico que o barbeiro nao faz nao conta como cadeira livre para ESTE pedido.
                .filter(p -> aptos == null || aptos.contains(p))
                .toList();
        List<UUID> livres = candidatos.stream()
                .filter(p -> dia.agendamentos().stream()
                        .noneMatch(i -> p.equals(i.professionalId()) && i.sobrepoe(inicio, fim)))
                .filter(p -> !scheduleBlockService.isBlocked(date, horaInicio, duracao, p))
                .toList();
        return new Checagem(!livres.isEmpty(), true, livres);
    }

    private static int minutos(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
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

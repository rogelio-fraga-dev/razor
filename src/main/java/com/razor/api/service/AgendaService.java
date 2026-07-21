package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaService {

    private static final ZoneId ZONE = AppointmentDates.ZONE;

    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;
    private final ScheduledTaskRepository taskRepository;
    private final com.razor.api.repository.CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final RazorProperties properties;
    private final ProfessionalAssignmentService professionalAssignmentService;
    private final com.razor.api.repository.ProfessionalRepository professionalRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Classe auxiliar de leitura para manter a migração legada funcionando
    @Data
    public static class AgendamentoLegado {
        private String name;
        private String phone;
        private String time;
        private String date;
        private String service;
        private boolean notified = false;
        private boolean thanked = false;
        private String paymentStatus = "PENDENTE";
    }

    @PostConstruct
    public void init() {
        carregarEMigrarAgendaDeArquivo();
    }

    public void salvarAgendaEmArquivo() {
        // Obsoleto - agora salva no Postgres direto via Repository.
    }

    private void carregarEMigrarAgendaDeArquivo() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("agenda.json");
            if (java.nio.file.Files.exists(path)) {
                String json = java.nio.file.Files.readString(path);
                List<AgendamentoLegado> list = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, AgendamentoLegado.class));

                log.info("[AGENDA] Iniciando migração de {} registros do agenda.json para o PostgreSQL...", list.size());

                for (AgendamentoLegado leg : list) {
                    LocalDate date;
                    LocalTime time;
                    try {
                        date = AppointmentDates.parseDate(leg.getDate());
                        time = AppointmentDates.parseTime(leg.getTime());
                    } catch (Exception e) {
                        log.warn("[AGENDA] Registro legado ignorado (data/hora invalida: '{}' '{}'): {}", leg.getDate(), leg.getTime(), e.getMessage());
                        continue;
                    }
                    Appointment app = Appointment.builder()
                        .barbershopId(TenantContext.getCurrent())
                        .customerName(leg.getName())
                        .customerPhone(leg.getPhone() != null ? leg.getPhone() : "N/A")
                        .appointmentTime(time)
                        .appointmentDate(date)
                        .service(leg.getService())
                        .paymentStatus(PaymentStatus.fromString(leg.getPaymentStatus()))
                        .notified(leg.isNotified())
                        .thanked(leg.isThanked())
                        .build();
                    appointmentRepository.save(app);
                }

                log.info("[AGENDA] Migração concluída. Renomeando arquivo para agenda.json.bkp");
                java.nio.file.Path backupPath = java.nio.file.Paths.get("agenda.json.bkp");
                java.nio.file.Files.move(path, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            } else {
                log.info("[AGENDA] Nenhum arquivo agenda.json encontrado para migração. Utilizando PostgreSQL.");
            }
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao carregar/migrar agenda do arquivo JSON: {}", e.getMessage());
        }
    }

    private void criarTarefasAgendadas(Appointment ag) {
        if (ag.getCustomerPhone() == null || ag.getCustomerPhone().trim().isEmpty() || ag.getCustomerPhone().equals("N/A")) return;
        if (ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) return;

        try {
            LocalDateTime apptDateTime = LocalDateTime.of(ag.getAppointmentDate(), ag.getAppointmentTime());

            // Se já passou, não agenda lembrete
            if (apptDateTime.isBefore(LocalDateTime.now(ZONE))) {
                return;
            }

            String pNome = ag.getCustomerName().split(" ")[0];

            // 1. Tarefa de LEMBRETE (1 hora antes do horário)
            LocalDateTime reminderTime = apptDateTime.minusHours(1);
            String reminderMsg = "Olá, " + pNome + "! 💈\nLembramos que você tem um agendamento hoje às *" + AppointmentDates.formatTime(ag.getAppointmentTime()) + "* para *" + ag.getService() + "* na Barbearia Ficticia.";

            ScheduledTask reminderTask = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(ag.getCustomerPhone())
                    .executionTime(reminderTime)
                    .taskType("REMINDER")
                    .messageContent(reminderMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reminderTask);
            log.info("[AGENDA] Tarefa de LEMBRETE criada para {} às {}", ag.getCustomerPhone(), reminderTime);

            // 2. Tarefa de AVALIAÇÃO (1 hora depois do horário)
            LocalDateTime reviewTime = apptDateTime.plusHours(1);
            String reviewMsg = "E aí, " + pNome + ", curtiu o talento? 🧔‍♂️\nSua avaliação ajuda a barbearia a crescer! Leva só 1 minuto:\n⭐ " + properties.getMenu().getAddressMapsUrl() + "\n\nValeu demais! 👊";

            ScheduledTask reviewTask = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(ag.getCustomerPhone())
                    .executionTime(reviewTime)
                    .taskType("REVIEW_REQUEST")
                    .messageContent(reviewMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reviewTask);
            log.info("[AGENDA] Tarefa de AVALIAÇÃO criada para {} às {}", ag.getCustomerPhone(), reviewTime);

        } catch (Exception e) {
            log.error("[AGENDA] Erro ao criar tarefas agendadas para {}: {}", ag.getCustomerPhone(), e.getMessage());
        }
    }

    private void cancelarTarefasBanco(String phone) {
        try {
            List<ScheduledTask> tasks = taskRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);
            List<ScheduledTask> pendingToCancel = tasks.stream()
                    .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.PENDING)
                    .toList();

            for (ScheduledTask task : pendingToCancel) {
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                taskRepository.save(task);
            }
            log.info("[AGENDA] Canceladas {} tarefas pendentes (incluindo expiradas) para o telefone {}", pendingToCancel.size(), phone);
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do banco para {}: {}", phone, e.getMessage());
        }
    }

    public void adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        adicionarAgendamentoViaIA(name, phone, date, time, service, null);
    }

    /**
     * Cria um agendamento e atribui um profissional (barbeiro): usa o {@code preferredProfessionalId}
     * se informado e livre; caso contrario auto-atribui o barbeiro livre menos ocupado no dia.
     */
    public void adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service,
                                          java.util.UUID preferredProfessionalId) {
        LocalDate parsedDate;
        LocalTime parsedTime;
        try {
            parsedDate = AppointmentDates.parseDate(date);
            parsedTime = AppointmentDates.parseTime(time);
        } catch (Exception e) {
            log.error("[AGENDA] Nao foi possivel criar agendamento para {} — data/hora invalida ('{}' '{}'): {}", phone, date, time, e.getMessage());
            return;
        }

        java.util.UUID professionalId = professionalAssignmentService.assign(parsedDate, parsedTime, preferredProfessionalId);

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

        criarTarefasAgendadas(app);

        try {
            com.razor.api.model.Customer customer = customerRepository
                    .findByBarbershopIdAndPhoneNumber(TenantContext.getCurrent(), phone).orElse(null);
            if (customer != null) {
                int currentPoints = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
                int newPoints = currentPoints + 1;
                customer.setFidelityPoints(newPoints);
                customer.setLastServiceDate(LocalDateTime.now());
                customerRepository.save(customer);
                log.info("[FIDELIDADE] Adicionado 1 ponto para {}. Total: {}", phone, newPoints);

                if (newPoints >= 10) {
                    customer.setFidelityPoints(0);
                    customerRepository.save(customer);

                    String coupon = "FIDELIDADE10-" + java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
                    String fidelityRewardMsg = "🎉 *PARABÉNS!* Você completou 10 pontos no seu Cartão Fidelidade da Barbearia Ficticia! 🥳✂️\n\n" +
                            "Como recompensa, você ganhou *10% de desconto* no seu próximo corte! Apresente o código abaixo no caixa:\n\n" +
                            "🎟️ *" + coupon + "*\n\n" +
                            "Obrigado pela preferência e por fazer parte da nossa história! 👊💈";
                    whatsAppService.sendTextMessage(phone, fidelityRewardMsg);
                    log.info("[FIDELIDADE] Cliente {} atingiu 10 pontos e ganhou cupom: {}", phone, coupon);
                }
            }
        } catch (Exception ex) {
            log.error("[FIDELIDADE] Erro ao atualizar pontos para {}: {}", phone, ex.getMessage());
        }
    }

    public String getHorariosOcupadosParaIA() {
        java.util.UUID shop = TenantContext.getCurrent();
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(shop);
        if (agendaAtiva.isEmpty()) return "Agenda totalmente livre. Todos os horários estão disponíveis.";

        // Capacidade = nº de barbeiros ativos (cadeiras). Na agenda multiprofissional um
        // horário só fica INDISPONÍVEL quando TODAS as cadeiras estão ocupadas nele.
        int capacidade = Math.max(1, professionalRepository.findByBarbershopIdAndActiveTrue(shop).size());

        // Conta agendamentos por (data, hora); TreeMap ordena os horários (HH:mm).
        java.util.Map<String, java.util.Map<String, Integer>> porDataHora = new java.util.LinkedHashMap<>();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) continue;
            String d = AppointmentDates.formatDate(ag.getAppointmentDate());
            String h = AppointmentDates.formatTime(ag.getAppointmentTime());
            porDataHora.computeIfAbsent(d, k -> new java.util.TreeMap<>()).merge(h, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder("Horários INDISPONÍVEIS por data (todas as " + capacidade + " cadeiras ocupadas):\n");
        boolean algum = false;
        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> entry : porDataHora.entrySet()) {
            List<String> cheios = new ArrayList<>();
            for (java.util.Map.Entry<String, Integer> he : entry.getValue().entrySet()) {
                if (he.getValue() >= capacidade) cheios.add(he.getKey());
            }
            if (!cheios.isEmpty()) {
                algum = true;
                sb.append("  - ").append(entry.getKey()).append(": ").append(String.join(", ", cheios)).append("\n");
            }
        }
        if (!algum) return "Agenda totalmente livre. Todos os horários estão disponíveis.";
        return sb.toString().trim();
    }

    public String getHorariosDisponiveisDoDia(String date) {
        LocalDate target;
        try {
            target = AppointmentDates.parseDate(date);
        } catch (Exception e) {
            return "Nenhum horário disponível para este dia.";
        }

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        List<String> ocupadosDoDia = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            if (target.equals(ag.getAppointmentDate()) && ag.getAppointmentTime() != null) {
                ocupadosDoDia.add(AppointmentDates.formatTime(ag.getAppointmentTime()));
            }
        }
        return montarHorariosLivres(target, ocupadosDoDia);
    }

    /**
     * Horarios disponiveis de UM profissional (agenda multiprofissional): a ocupacao
     * considera apenas os agendamentos daquele barbeiro, entao dois profissionais podem
     * ter o mesmo horario livre de forma independente.
     */
    public String getHorariosDisponiveisDoProfissional(java.util.UUID professionalId, String date) {
        LocalDate target;
        try {
            target = AppointmentDates.parseDate(date);
        } catch (Exception e) {
            return "Nenhum horário disponível para este dia.";
        }

        List<Appointment> apps = appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(
                TenantContext.getCurrent(), professionalId, target);
        List<String> ocupadosDoDia = new ArrayList<>();
        for (Appointment ag : apps) {
            if (ag.getAppointmentTime() != null) {
                ocupadosDoDia.add(AppointmentDates.formatTime(ag.getAppointmentTime()));
            }
        }
        return montarHorariosLivres(target, ocupadosDoDia);
    }

    /** Gera ate 4 horarios livres (espacados) das 09:00 as 18:30, excluindo ocupados e passados. */
    private String montarHorariosLivres(LocalDate target, List<String> ocupadosDoDia) {
        List<String> livres = new ArrayList<>();
        LocalTime inicio = LocalTime.of(9, 0);
        LocalTime fim = LocalTime.of(19, 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime cursor = inicio;

        // Verifica se a data consultada é hoje
        boolean isHoje = target.equals(AppointmentDates.today());
        LocalTime agora = LocalTime.now(ZONE);

        while (!cursor.isAfter(fim.minusMinutes(30))) {
            String horario = cursor.format(fmt);

            boolean isPassado = isHoje && cursor.isBefore(agora);

            if (!ocupadosDoDia.contains(horario) && !isPassado) {
                livres.add(horario);
            }
            cursor = cursor.plusMinutes(30);
        }
        if (livres.isEmpty()) return "Nenhum horário disponível para este dia.";

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

    public Appointment getAgendamentoCliente(String phone) {
        return appointmentRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone).stream().findFirst().orElse(null);
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

    public List<Appointment> getAgendamentosClienteList(String phone) {
        return appointmentRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);
    }

    public boolean cancelarAgendamentoExato(Appointment ag) {
        if (ag != null) {
            appointmentRepository.delete(ag);
            cancelarTarefasClienteExato(ag.getCustomerPhone());
            return true;
        }
        return false;
    }

    public void cancelarTarefasClienteExato(String phone) {
        try {
            List<ScheduledTask> tasks = taskRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);
            List<ScheduledTask> pendingToCancel = tasks.stream()
                    .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.PENDING)
                    .toList();

            for (ScheduledTask task : pendingToCancel) {
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                taskRepository.save(task);
            }
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do banco para {}: {}", phone, e.getMessage());
        }
    }

    public boolean cancelarAgendamentoCliente(String phone) {
        Appointment ag = getAgendamentoCliente(phone);
        if (ag != null) {
            appointmentRepository.delete(ag);
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            cancelarTarefasBanco(phone);
            return true;
        }
        return false;
    }

    public void agendarDisparoMassa(String msg, LocalDateTime execTime) {
        List<com.razor.api.model.Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
        for (com.razor.api.model.Customer c : customers) {
            boolean isAdmin = false;
            for (String admPhone : properties.getAdmin().getPhones()) {
                if (c.getPhoneNumber().equals(admPhone)) {
                    isAdmin = true;
                    break;
                }
            }
            if (isAdmin) continue;
            ScheduledTask task = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(c.getPhoneNumber())
                    .executionTime(execTime)
                    .taskType("BROADCAST_SCHEDULED")
                    .messageContent(msg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(task);
        }
        log.info("[AGENDA] Disparo em massa agendado para {} contatos em {}", customers.size() - 1, execTime);
    }

    public String getAgendaSalva() {
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        if (agendaAtiva.isEmpty()) return "Nenhum agendamento registrado no sistema.";

        List<String> display = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            display.add("📅 " + AppointmentDates.formatDate(ag.getAppointmentDate()) + " às ⏰ " + AppointmentDates.formatTime(ag.getAppointmentTime()) + " - " + ag.getCustomerName() + " | " + ag.getService());
        }
        return String.join("\n", display);
    }

    public String getResumoDoDia() {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);
        int totalCortes = 0;
        double faturamentoEstimado = 0.0;

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Appointment ag : agendaAtiva) {
            if (hoje.equals(ag.getAppointmentDate())) {
                totalCortes++;
                String serv = ag.getService().toLowerCase();
                if (serv.contains("combo")) faturamentoEstimado += 60.0;
                else if (serv.contains("barba")) faturamentoEstimado += 30.0;
                else faturamentoEstimado += 35.0;
            }
        }
        return String.format("📈 *Fechamento de %s*\n\n✂️ Cortes realizados hoje: %d\n💰 Faturamento estimado: R$ %.2f", dataHoje, totalCortes, faturamentoEstimado);
    }

    public int getAgendamentosHojeCount() {
        LocalDate hoje = AppointmentDates.today();
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        return (int) agendaAtiva.stream().filter(a -> hoje.equals(a.getAppointmentDate())).count();
    }

    public String getAgendaHojeText() {
        LocalDate hoje = AppointmentDates.today();
        List<Appointment> apps = appointmentRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
            .filter(a -> hoje.equals(a.getAppointmentDate()))
            .collect(Collectors.toList());

        if (apps.isEmpty()) return "Nenhum agendamento para hoje.";

        StringBuilder sb = new StringBuilder();
        for (Appointment ag : apps) {
            sb.append("⏰ ").append(AppointmentDates.formatTime(ag.getAppointmentTime())).append(" - ").append(ag.getCustomerName()).append(" | ").append(ag.getService()).append("\n");
        }
        return sb.toString().trim();
    }

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

                        com.razor.api.model.Customer customer = customerService.findByName(name);
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
                            criarTarefasAgendadas(app);
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

    public String getResumoDetelhadoDoDia() {
        return getResumoFinanceiroDetalhado();
    }

    public String getResumoFinanceiroDetalhado() {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);

        int totalCortes = 0;
        int totalCombo = 0;
        int totalBarba = 0;
        int totalSimples = 0;

        double faturamentoTotal = 0.0;
        double pixVal = 0.0;
        double presencialVal = 0.0;
        double pendenteVal = 0.0;

        int pixCount = 0;
        int presencialCount = 0;
        int pendenteCount = 0;

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Appointment ag : agendaAtiva) {
            if (hoje.equals(ag.getAppointmentDate())) {
                totalCortes++;
                String serv = ag.getService().toLowerCase();
                double valorServico = 35.0; // Padrão

                if (serv.contains("combo")) {
                    valorServico = 60.0;
                    totalCombo++;
                } else if (serv.contains("barba")) {
                    valorServico = 30.0;
                    totalBarba++;
                } else {
                    totalSimples++;
                }

                faturamentoTotal += valorServico;

                PaymentStatus status = ag.getPaymentStatus();
                if (status == PaymentStatus.PIX) {
                    pixCount++;
                    pixVal += valorServico;
                } else if (status == PaymentStatus.PRESENCIAL) {
                    presencialCount++;
                    presencialVal += valorServico;
                } else {
                    pendenteCount++;
                    pendenteVal += valorServico;
                }
            }
        }

        double ticketMedio = totalCortes > 0 ? faturamentoTotal / totalCortes : 0.0;

        return String.format("""
                📊 *EXTRATO FINANCEIRO DO DIA*
                🗓️ Data: *%s*
                ━━━━━━━━━━━━━━━━━━━━━━

                ✂️ *VOLUME DE SERVIÇOS*
                Total Atendidos: *%d*
                ├ Combos (Cabelo+Barba): %d
                ├ Apenas Barba: %d
                └ Simples / Outros: %d

                💳 *FLUXO DE CAIXA*
                🟢 *PIX:* R$ %.2f (%d)
                🔵 *Presencial:* R$ %.2f (%d)
                🟡 *Pendente:* R$ %.2f (%d)

                ━━━━━━━━━━━━━━━━━━━━━━
                💰 *Faturamento Total:* R$ %.2f
                📈 *Ticket Médio:* R$ %.2f
                ━━━━━━━━━━━━━━━━━━━━━━
                """,
                dataHoje, totalCortes, totalCombo, totalBarba, totalSimples,
                pixVal, pixCount, presencialVal, presencialCount, pendenteVal, pendenteCount,
                faturamentoTotal, ticketMedio);
    }

    public String getPainelAgendamentosDetalhado(long totalBase, long waitlistCount, long pausedCount) {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        List<Appointment> agendaHoje = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            if (hoje.equals(ag.getAppointmentDate())) {
                agendaHoje.add(ag);
            }
        }

        // Ordenação Cronológica (Horário)
        agendaHoje.sort((a1, a2) -> compareByTime(a1, a2));

        List<String> list = new ArrayList<>();
        for (Appointment ag : agendaHoje) {
            String badge = "";
            if (ag.getPaymentStatus() == PaymentStatus.PIX) badge = " 🟢 [PIX]";
            else if (ag.getPaymentStatus() == PaymentStatus.PRESENCIAL) badge = " 🔵 [Local]";
            else badge = " 🟡 [Pend]";

            list.add(String.format("⏰ *%s* | %s (%s)%s", AppointmentDates.formatTime(ag.getAppointmentTime()), ag.getCustomerName(), ag.getService(), badge));
        }

        String agendaStr = list.isEmpty() ? "_Nenhum agendamento para hoje até o momento._" : String.join("\n", list);

        return String.format("""
                📅 *PAINEL DE AGENDAMENTOS*
                🗓️ Data: *%s*
                ━━━━━━━━━━━━━━━━━━━━━━
                👥 *Métricas da Base*
                ├ Clientes Cadastrados: *%d*
                ├ Fila de Espera: *%d*
                └ Em Atendimento Humano: *%d*

                📋 *Agendamentos de Hoje (%d)*
                ━━━━━━━━━━━━━━━━━━━━━━
                %s
                ━━━━━━━━━━━━━━━━━━━━━━
                """,
                dataHoje, totalBase, waitlistCount, pausedCount, agendaHoje.size(), agendaStr);
    }

    public String getAgendaSemanaText() {
        LocalDate hoje = AppointmentDates.today();
        LocalDate inicioSemana = hoje.with(java.time.DayOfWeek.MONDAY);
        LocalDate fimSemana = hoje.with(java.time.DayOfWeek.SUNDAY);

        java.util.Map<LocalDate, List<Appointment>> porDia = new java.util.TreeMap<>();

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Appointment ag : agendaAtiva) {
            LocalDate agDate = ag.getAppointmentDate();
            if (agDate == null) continue;
            if (!agDate.isBefore(inicioSemana) && !agDate.isAfter(fimSemana)) {
                porDia.computeIfAbsent(agDate, k -> new ArrayList<>()).add(ag);
            }
        }

        if (porDia.isEmpty()) {
            return "_Nenhum agendamento para esta semana._";
        }

        StringBuilder sb = new StringBuilder("📆 *AGENDA DA SEMANA*\n\n");
        for (java.util.Map.Entry<LocalDate, List<Appointment>> entry : porDia.entrySet()) {
            LocalDate dia = entry.getKey();
            String diaSemana = dia.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
            String title = AppointmentDates.formatDate(dia) + " - " + diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1);
            sb.append("📅 *").append(title).append("*\n");

            List<Appointment> appsDia = entry.getValue();
            appsDia.sort((a1, a2) -> compareByTime(a1, a2));

            for (Appointment ag : appsDia) {
                String badge = "";
                if (ag.getPaymentStatus() == PaymentStatus.PIX) badge = " [PIX]";
                else if (ag.getPaymentStatus() == PaymentStatus.PRESENCIAL) badge = " [Local]";
                else badge = " [Pend]";
                sb.append("  ⏰ ").append(AppointmentDates.formatTime(ag.getAppointmentTime())).append(" | ").append(ag.getCustomerName()).append(" (").append(ag.getService()).append(")").append(badge).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public String getResumoFinanceiroDaSemana() {
        LocalDate hoje = AppointmentDates.today();
        LocalDate inicioSemana = hoje.with(java.time.DayOfWeek.MONDAY);

        int totalCortes = 0;
        int totalCombo = 0;
        int totalBarba = 0;
        int totalSimples = 0;

        double faturamentoTotal = 0.0;
        double pixVal = 0.0;
        double presencialVal = 0.0;
        double pendenteVal = 0.0;

        int pixCount = 0;
        int presencialCount = 0;
        int pendenteCount = 0;

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Appointment ag : agendaAtiva) {
            LocalDate agDate = ag.getAppointmentDate();
            if (agDate == null) continue;

            // Só conta até "hoje" na semana atual!
            if (!agDate.isBefore(inicioSemana) && !agDate.isAfter(hoje)) {
                totalCortes++;
                String serv = ag.getService().toLowerCase();
                double valorServico = 35.0; // Padrão

                if (serv.contains("combo")) {
                    valorServico = 60.0;
                    totalCombo++;
                } else if (serv.contains("barba")) {
                    valorServico = 30.0;
                    totalBarba++;
                } else {
                    totalSimples++;
                }

                faturamentoTotal += valorServico;

                PaymentStatus status = ag.getPaymentStatus();
                if (status == PaymentStatus.PIX) {
                    pixCount++;
                    pixVal += valorServico;
                } else if (status == PaymentStatus.PRESENCIAL) {
                    presencialCount++;
                    presencialVal += valorServico;
                } else {
                    pendenteCount++;
                    pendenteVal += valorServico;
                }
            }
        }

        double ticketMedio = totalCortes > 0 ? faturamentoTotal / totalCortes : 0.0;
        String periodo = AppointmentDates.formatDate(inicioSemana) + " até " + AppointmentDates.formatDate(hoje);

        return String.format("""
                📊 *RESUMO FINANCEIRO DA SEMANA*
                _Acumulado de %s (Até hoje)_

                ✂️ *Volume de Serviços Realizados:*
                - Total de Atendimentos: %d
                - Combos: %d
                - Apenas Barba: %d
                - Cortes Simples / Outros: %d

                💳 *Fluxo de Caixa por Meio de Pagamento:*
                - ✅ *PIX (Confirmado):* R$ %.2f (%d rec.)
                - 💵 *Presencial:* R$ %.2f (%d rec.)
                - ⏳ *Pendente:* R$ %.2f (%d agendamentos)

                📈 *Indicadores de Performance:*
                - Faturamento Parcial: *R$ %.2f*
                - Ticket Médio na Semana: *R$ %.2f*
                """,
                periodo, totalCortes, totalCombo, totalBarba, totalSimples,
                pixVal, pixCount, presencialVal, presencialCount, pendenteVal, pendenteCount,
                faturamentoTotal, ticketMedio);
    }

    public void agendarNotificacaoRetorno(String phone, int minutesDelay) {
        ScheduledTask task = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                .customerPhone(phone)
                .taskType("UNPAUSE_NOTIFY")
                .executionTime(LocalDateTime.now(ZONE).plusMinutes(minutesDelay))
                .messageContent("Voltamos! 🤖\n\nMeu atendimento automático foi retomado. Se precisar de algo, é só chamar!")
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
        taskRepository.save(task);
        log.info("[AGENDA] Notificação de retorno agendada para {} em {} minutos", phone, minutesDelay);
    }

    /** Ordena agendamentos por horário, tolerando horários nulos (vão para o fim). */
    private int compareByTime(Appointment a1, Appointment a2) {
        LocalTime t1 = a1.getAppointmentTime();
        LocalTime t2 = a2.getAppointmentTime();
        if (t1 == null && t2 == null) return 0;
        if (t1 == null) return 1;
        if (t2 == null) return -1;
        return t1.compareTo(t2);
    }
}

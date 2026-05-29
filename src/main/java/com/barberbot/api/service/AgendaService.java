package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.model.Appointment;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.AppointmentRepository;
import com.barberbot.api.repository.ScheduledTaskRepository;
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

    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;
    private final ScheduledTaskRepository taskRepository;
    private final com.barberbot.api.repository.CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final BarberBotProperties properties;
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
                    Appointment app = Appointment.builder()
                        .customerName(leg.getName())
                        .customerPhone(leg.getPhone() != null ? leg.getPhone() : "N/A")
                        .appointmentTime(leg.getTime())
                        .appointmentDate(leg.getDate())
                        .service(leg.getService())
                        .paymentStatus(leg.getPaymentStatus())
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
        
        try {
            // Parser de hora
            String cleanTime = ag.getAppointmentTime().trim().replace("h", ":").replace(" ", "");
            if (cleanTime.length() == 4 && cleanTime.charAt(1) == ':') cleanTime = "0" + cleanTime;
            LocalTime apptTime = LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("HH:mm"));
            
            // Parser de data
            String cleanDate = ag.getAppointmentDate().trim();
            if (cleanDate.equalsIgnoreCase("hoje")) {
                cleanDate = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
            }
            String[] parts = cleanDate.split("/");
            int day = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            int year = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
            
            LocalDateTime apptDateTime = LocalDateTime.of(year, month, day, apptTime.getHour(), apptTime.getMinute());
            
            // Se já passou, não agenda lembrete
            if (apptDateTime.isBefore(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")))) {
                return;
            }
            
            String pNome = ag.getCustomerName().split(" ")[0];
            
            // 1. Tarefa de LEMBRETE (1 hora antes do horário)
            LocalDateTime reminderTime = apptDateTime.minusHours(1);
            String reminderMsg = "Olá, " + pNome + "! 💈\nLembramos que você tem um agendamento hoje às *" + ag.getAppointmentTime() + "* para *" + ag.getService() + "* na Barbearia Ficticia.";
            
            ScheduledTask reminderTask = ScheduledTask.builder()
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
            
            ScheduledTask reviewTask = ScheduledTask.builder()
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
            List<ScheduledTask> tasks = taskRepository.findByCustomerPhone(phone);
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
        Appointment app = Appointment.builder()
            .customerName(name)
            .customerPhone(phone)
            .appointmentDate(date)
            .appointmentTime(time)
            .service(service)
            .paymentStatus("PENDENTE")
            .build();
        appointmentRepository.save(app);
        
        log.info("[AGENDA] Novo agendamento salvo: {} para o dia {} às {}", name, date, time);
        
        criarTarefasAgendadas(app);
        
        try {
            com.barberbot.api.model.Customer customer = customerRepository.findByPhoneNumber(phone).orElse(null);
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
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        if (agendaAtiva.isEmpty()) return "Agenda totalmente livre. Todos os horários estão disponíveis.";
        // Agrupa por data para clareza da IA e evitar falsos positivos entre dias diferentes
        java.util.Map<String, List<String>> porData = new java.util.LinkedHashMap<>();
        for (Appointment ag : agendaAtiva) {
            porData.computeIfAbsent(ag.getAppointmentDate(), k -> new ArrayList<>()).add(ag.getAppointmentTime());
        }
        StringBuilder sb = new StringBuilder("Horários INDISPONÍVEIS por data:\n");
        for (java.util.Map.Entry<String, List<String>> entry : porData.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
        }
        return sb.toString().trim();
    }

    public String getHorariosDisponiveisDoDia(String date) {
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        List<String> ocupadosDoDia = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate().equals(date) || (date.equalsIgnoreCase("hoje") && ag.getAppointmentDate().equals(LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"))))) {
                // Normaliza o horário para HH:MM para comparação
                String cleanTime = ag.getAppointmentTime().trim().replace("h", ":").replace(" ", "");
                if (cleanTime.length() == 4 && cleanTime.charAt(1) == ':') cleanTime = "0" + cleanTime;
                ocupadosDoDia.add(cleanTime);
            }
        }
        
        List<String> livres = new ArrayList<>();
        LocalTime inicio = LocalTime.of(9, 0);
        LocalTime fim = LocalTime.of(19, 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime cursor = inicio;
        
        // Verifica se a data consultada é hoje
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String dataHojeStr = hoje.format(DateTimeFormatter.ofPattern("dd/MM"));
        boolean isHoje = date.equals(dataHojeStr) || date.equalsIgnoreCase("hoje");
        LocalTime agora = LocalTime.now(ZoneId.of("America/Sao_Paulo"));

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
        return appointmentRepository.findByCustomerPhone(phone).stream().findFirst().orElse(null);
    }
    
    public boolean hasAgendamentoFuturo(String phone) {
        LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate hoje = agora.toLocalDate();
        List<Appointment> apps = appointmentRepository.findByCustomerPhone(phone);
        
        for (Appointment ag : apps) {
            try {
                String dateStr = ag.getAppointmentDate().equalsIgnoreCase("hoje") ? hoje.format(DateTimeFormatter.ofPattern("dd/MM")) : ag.getAppointmentDate();
                String[] parts = dateStr.split("/");
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                LocalDate agDate = LocalDate.of(hoje.getYear(), month, day);
                
                if (agDate.isAfter(hoje)) {
                    return true;
                } else if (agDate.isEqual(hoje)) {
                    String timeStr = ag.getAppointmentTime();
                    if (timeStr != null && timeStr.contains(":")) {
                        String[] tParts = timeStr.split(":");
                        int hour = Integer.parseInt(tParts[0].trim());
                        int minute = Integer.parseInt(tParts[1].trim());
                        LocalTime agTime = LocalTime.of(hour, minute);
                        
                        // Se o horário atual for ANTES do (horário agendado + 1 hora), consideramos como futuro/ativo
                        if (agora.toLocalTime().isBefore(agTime.plusHours(1))) {
                            return true;
                        }
                    } else {
                        // Sem horário válido, consideramos ativo hoje por segurança
                        return true;
                    }
                }
            } catch (Exception e) {}
        }
        return false;
    }
    
    public List<Appointment> getAgendamentosClienteList(String phone) {
        return appointmentRepository.findByCustomerPhone(phone);
    }

    public boolean cancelarAgendamentoExato(Appointment ag) {
        if (ag != null) {
            appointmentRepository.delete(ag);
            cancelarTarefasClienteExato(ag.getCustomerPhone(), ag.getAppointmentDate(), ag.getAppointmentTime());
            return true;
        }
        return false;
    }
    
    public void cancelarTarefasClienteExato(String phone, String date, String time) {
        try {
            List<ScheduledTask> tasks = taskRepository.findByCustomerPhone(phone);
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
        List<com.barberbot.api.model.Customer> customers = customerRepository.findAll();
        for (com.barberbot.api.model.Customer c : customers) {
            boolean isAdmin = false;
            for (String admPhone : properties.getAdmin().getPhones()) {
                if (c.getPhoneNumber().equals(admPhone)) {
                    isAdmin = true;
                    break;
                }
            }
            if (isAdmin) continue;
            ScheduledTask task = ScheduledTask.builder()
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
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        if (agendaAtiva.isEmpty()) return "Nenhum agendamento registrado no sistema.";
        
        List<String> display = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            display.add("📅 " + ag.getAppointmentDate() + " às ⏰ " + ag.getAppointmentTime() + " - " + ag.getCustomerName() + " | " + ag.getService());
        }
        return String.join("\n", display);
    }

    public String getResumoDoDia() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        int totalCortes = 0;
        double faturamentoEstimado = 0.0;

        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate().equals(dataHoje) || ag.getAppointmentDate().equalsIgnoreCase("hoje")) {
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
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        return (int) agendaAtiva.stream().filter(a -> a.getAppointmentDate().equals(dataHoje) || a.getAppointmentDate().equalsIgnoreCase("hoje")).count();
    }

    public String getAgendaHojeText() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        List<Appointment> apps = appointmentRepository.findAll().stream()
            .filter(a -> a.getAppointmentDate().equals(dataHoje) || a.getAppointmentDate().equalsIgnoreCase("hoje"))
            .collect(Collectors.toList());
            
        if (apps.isEmpty()) return "Nenhum agendamento para hoje.";
        
        StringBuilder sb = new StringBuilder();
        for (Appointment ag : apps) {
            sb.append("⏰ ").append(ag.getAppointmentTime()).append(" - ").append(ag.getCustomerName()).append(" | ").append(ag.getService()).append("\n");
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
                    String date = item.has("date") ? item.get("date").asText() : LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"));
                    String service = item.has("service") ? item.get("service").asText() : "Corte";

                    if (!name.isEmpty() && !time.isEmpty()) {
                        com.barberbot.api.model.Customer customer = customerService.findByName(name);
                        String phone = (customer != null) ? customer.getPhoneNumber() : null;
                        
                        Appointment app = Appointment.builder()
                            .customerName(name)
                            .customerPhone(phone != null ? phone : "N/A")
                            .appointmentTime(time)
                            .appointmentDate(date)
                            .service(service)
                            .paymentStatus("PENDENTE")
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
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        
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

        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate().equals(dataHoje) || ag.getAppointmentDate().equalsIgnoreCase("hoje")) {
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
                
                String status = ag.getPaymentStatus();
                if ("PIX".equalsIgnoreCase(status)) {
                    pixCount++;
                    pixVal += valorServico;
                } else if ("PRESENCIAL".equalsIgnoreCase(status)) {
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
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        List<Appointment> agendaHoje = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            if (ag.getAppointmentDate().equals(dataHoje) || ag.getAppointmentDate().equalsIgnoreCase("hoje")) {
                agendaHoje.add(ag);
            }
        }
        
        // Ordenação Cronológica (Horário)
        agendaHoje.sort((a1, a2) -> {
            try {
                return LocalTime.parse(a1.getAppointmentTime()).compareTo(LocalTime.parse(a2.getAppointmentTime()));
            } catch (Exception e) {
                return 0;
            }
        });

        List<String> list = new ArrayList<>();
        for (Appointment ag : agendaHoje) {
            String badge = "";
            if ("PIX".equalsIgnoreCase(ag.getPaymentStatus())) badge = " 🟢 [PIX]";
            else if ("PRESENCIAL".equalsIgnoreCase(ag.getPaymentStatus())) badge = " 🔵 [Local]";
            else badge = " 🟡 [Pend]";
            
            list.add(String.format("⏰ *%s* | %s (%s)%s", ag.getAppointmentTime(), ag.getCustomerName(), ag.getService(), badge));
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
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate inicioSemana = hoje.with(java.time.DayOfWeek.MONDAY);
        LocalDate fimSemana = hoje.with(java.time.DayOfWeek.SUNDAY);
        
        java.util.Map<String, List<Appointment>> porDia = new java.util.TreeMap<>();
        
        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        for (Appointment ag : agendaAtiva) {
            try {
                String dateStr = ag.getAppointmentDate().equalsIgnoreCase("hoje") ? hoje.format(DateTimeFormatter.ofPattern("dd/MM")) : ag.getAppointmentDate();
                String[] parts = dateStr.split("/");
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                LocalDate agDate = LocalDate.of(hoje.getYear(), month, day);
                
                if (!agDate.isBefore(inicioSemana) && !agDate.isAfter(fimSemana)) {
                    String diaSemana = agDate.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
                    String key = String.format("2026-%02d-%02d | %02d/%02d - %s", month, day, day, month, diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1));
                    
                    porDia.computeIfAbsent(key, k -> new ArrayList<>()).add(ag);
                }
            } catch (Exception e) {}
        }
        
        if (porDia.isEmpty()) {
            return "_Nenhum agendamento para esta semana._";
        }
        
        StringBuilder sb = new StringBuilder("📆 *AGENDA DA SEMANA*\n\n");
        for (java.util.Map.Entry<String, List<Appointment>> entry : porDia.entrySet()) {
            String title = entry.getKey().split("\\|")[1].trim(); // Extrai só "DD/MM - Dia"
            sb.append("📅 *").append(title).append("*\n");
            
            List<Appointment> appsDia = entry.getValue();
            appsDia.sort((a1, a2) -> {
                try {
                    return LocalTime.parse(a1.getAppointmentTime()).compareTo(LocalTime.parse(a2.getAppointmentTime()));
                } catch (Exception e) { return 0; }
            });
            
            for (Appointment ag : appsDia) {
                String badge = "";
                if ("PIX".equalsIgnoreCase(ag.getPaymentStatus())) badge = " [PIX]";
                else if ("PRESENCIAL".equalsIgnoreCase(ag.getPaymentStatus())) badge = " [Local]";
                else badge = " [Pend]";
                sb.append("  ⏰ ").append(ag.getAppointmentTime()).append(" | ").append(ag.getCustomerName()).append(" (").append(ag.getService()).append(")").append(badge).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public String getResumoFinanceiroDaSemana() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
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

        List<Appointment> agendaAtiva = appointmentRepository.findAll();
        for (Appointment ag : agendaAtiva) {
            try {
                String dateStr = ag.getAppointmentDate().equalsIgnoreCase("hoje") ? hoje.format(DateTimeFormatter.ofPattern("dd/MM")) : ag.getAppointmentDate();
                String[] parts = dateStr.split("/");
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                LocalDate agDate = LocalDate.of(hoje.getYear(), month, day);
                
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
                    
                    String status = ag.getPaymentStatus();
                    if ("PIX".equalsIgnoreCase(status)) {
                        pixCount++;
                        pixVal += valorServico;
                    } else if ("PRESENCIAL".equalsIgnoreCase(status)) {
                        presencialCount++;
                        presencialVal += valorServico;
                    } else {
                        pendenteCount++;
                        pendenteVal += valorServico;
                    }
                }
            } catch (Exception e) {}
        }
        
        double ticketMedio = totalCortes > 0 ? faturamentoTotal / totalCortes : 0.0;
        String periodo = inicioSemana.format(DateTimeFormatter.ofPattern("dd/MM")) + " até " + hoje.format(DateTimeFormatter.ofPattern("dd/MM"));
        
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
        ScheduledTask task = ScheduledTask.builder()
                .customerPhone(phone)
                .taskType("UNPAUSE_NOTIFY")
                .executionTime(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).plusMinutes(minutesDelay))
                .messageContent("Voltamos! 🤖\n\nMeu atendimento automático foi retomado. Se precisar de algo, é só chamar!")
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
        taskRepository.save(task);
        log.info("[AGENDA] Notificação de retorno agendada para {} em {} minutos", phone, minutesDelay);
    }
}
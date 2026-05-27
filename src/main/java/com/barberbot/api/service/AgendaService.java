package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.model.Customer;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.ScheduledTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AgendaService {

    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;
    private final ScheduledTaskRepository taskRepository;
    private final BarberBotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Data
    public static class Agendamento {
        private String name;
        private String phone;
        private String time;
        private String date; 
        private String service;
        private boolean notified = false;
        private boolean thanked = false;
        private String paymentStatus = "PENDENTE";
    }

    private static final List<Agendamento> agendaAtiva = new CopyOnWriteArrayList<>();
    private static final List<String> agendaDisplay = new CopyOnWriteArrayList<>(); 

    @PostConstruct
    public void init() {
        carregarAgendaDeArquivo();
    }

    public void salvarAgendaEmArquivo() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("agenda.json");
            String json = objectMapper.writeValueAsString(agendaAtiva);
            java.nio.file.Files.writeString(path, json);
            log.info("[AGENDA] Agenda ativa salva localmente em agenda.json.");
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao salvar agenda em arquivo: {}", e.getMessage());
        }
    }

    private void carregarAgendaDeArquivo() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("agenda.json");
            if (java.nio.file.Files.exists(path)) {
                String json = java.nio.file.Files.readString(path);
                List<Agendamento> list = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Agendamento.class));
                agendaAtiva.clear();
                agendaAtiva.addAll(list);
                
                agendaDisplay.clear();
                for (Agendamento ag : agendaAtiva) {
                    agendaDisplay.add("📅 " + ag.getDate() + " às ⏰ " + ag.getTime() + " - " + ag.getName() + " | " + ag.getService());
                }
                log.info("[AGENDA] Agenda ativa carregada com sucesso do arquivo. Total: {}", agendaAtiva.size());
            } else {
                log.info("[AGENDA] Nenhum arquivo agenda.json encontrado. Iniciando agenda vazia.");
            }
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao carregar agenda do arquivo: {}", e.getMessage());
        }
    }

    private void criarTarefasAgendadas(Agendamento ag) {
        if (ag.getPhone() == null || ag.getPhone().trim().isEmpty()) return;
        
        try {
            // Parser de hora
            String cleanTime = ag.getTime().trim().replace("h", ":").replace(" ", "");
            if (cleanTime.length() == 4 && cleanTime.charAt(1) == ':') cleanTime = "0" + cleanTime;
            LocalTime apptTime = LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("HH:mm"));
            
            // Parser de data
            String[] dateParts = ag.getDate().split("/");
            int day = Integer.parseInt(dateParts[0].trim());
            int month = Integer.parseInt(dateParts[1].trim());
            int year = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
            
            LocalDate apptDate = LocalDate.of(year, month, day);
            LocalDateTime apptDateTime = LocalDateTime.of(apptDate, apptTime);
            
            String pNome = ag.getName().split(" ")[0];
            
            // 1. Tarefa de LEMBRETE (1 hora antes do horário)
            LocalDateTime reminderTime = apptDateTime.minusHours(1);
            String reminderMsg = "Olá, " + pNome + "! 💈\nLembramos que você tem um agendamento hoje às *" + ag.getTime() + "* para *" + ag.getService() + "* na Barbearia Ficticia.";
            
            ScheduledTask reminderTask = ScheduledTask.builder()
                    .customerPhone(ag.getPhone())
                    .executionTime(reminderTime)
                    .taskType("REMINDER")
                    .messageContent(reminderMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reminderTask);
            log.info("[AGENDA] Tarefa de LEMBRETE criada para {} às {}", ag.getPhone(), reminderTime);
            
            // 2. Tarefa de AVALIAÇÃO (1 hora depois do horário)
            LocalDateTime reviewTime = apptDateTime.plusHours(1);
            String reviewMsg = "E aí, " + pNome + ", curtiu o talento? 🧔‍♂️\nSua avaliação ajuda a barbearia a crescer! Leva só 1 minuto:\n⭐ " + properties.getMenu().getAddressMapsUrl() + "\n\nValeu demais! 👊";
            
            ScheduledTask reviewTask = ScheduledTask.builder()
                    .customerPhone(ag.getPhone())
                    .executionTime(reviewTime)
                    .taskType("REVIEW_REQUEST")
                    .messageContent(reviewMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reviewTask);
            log.info("[AGENDA] Tarefa de AVALIAÇÃO criada para {} às {}", ag.getPhone(), reviewTime);
            
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao criar tarefas no banco para {}: {}", ag.getName(), e.getMessage());
        }
    }

    private void cancelarTarefasBanco(String phone) {
        try {
            List<ScheduledTask> tasks = taskRepository.findByCustomerPhone(phone);
            List<ScheduledTask> pendingToCancel = tasks.stream()
                    .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.PENDING && t.getExecutionTime().isAfter(LocalDateTime.now()))
                    .toList();
            
            for (ScheduledTask task : pendingToCancel) {
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                taskRepository.save(task);
            }
            log.info("[AGENDA] Canceladas {} tarefas pendentes para o telefone {}", pendingToCancel.size(), phone);
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do banco para {}: {}", phone, e.getMessage());
        }
    }

    public void adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        Agendamento ag = new Agendamento();
        ag.setName(name);
        ag.setPhone(phone);
        ag.setDate(date);
        ag.setTime(time);
        ag.setService(service);
        agendaAtiva.add(ag);
        agendaDisplay.add("✅ " + date + " às " + time + " - " + name + " | " + service + " (Via IA)");
        log.info("[AGENDA] Novo agendamento salvo: {} para o dia {} às {}", name, date, time);
        
        salvarAgendaEmArquivo();
        criarTarefasAgendadas(ag);
    }

    public String getHorariosOcupadosParaIA() {
        if (agendaAtiva.isEmpty()) return "Agenda totalmente livre.";
        List<String> ocupados = new ArrayList<>();
        for (Agendamento ag : agendaAtiva) {
            ocupados.add(ag.getDate() + " às " + ag.getTime());
        }
        return "Horários INDISPONÍVEIS: " + String.join(" | ", ocupados);
    }

    public Agendamento getAgendamentoCliente(String phone) {
        return agendaAtiva.stream()
                .filter(a -> a.getPhone() != null && a.getPhone().equals(phone))
                .findFirst().orElse(null);
    }

    public boolean cancelarAgendamentoCliente(String phone) {
        Agendamento ag = getAgendamentoCliente(phone);
        if (ag != null) {
            agendaAtiva.remove(ag);
            agendaDisplay.removeIf(d -> d.contains(ag.getTime()) && d.contains(ag.getName()));
            log.info("[AGENDA] Cancelamento feito para o cliente do número: {}", phone);
            
            salvarAgendaEmArquivo();
            cancelarTarefasBanco(phone);
            return true;
        }
        return false;
    }

    public String getAgendaSalva() {
        if (agendaDisplay.isEmpty()) return "Nenhum agendamento registrado no sistema.";
        return String.join("\n", agendaDisplay);
    }

    public String getResumoDoDia() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        int totalCortes = 0;
        double faturamentoEstimado = 0.0;

        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                totalCortes++;
                String serv = ag.getService().toLowerCase();
                if (serv.contains("combo")) faturamentoEstimado += 60.0;
                else if (serv.contains("barba")) faturamentoEstimado += 30.0;
                else faturamentoEstimado += 35.0; 
            }
        }
        return String.format("📈 *Fechamento de %s*\n\n✂️ Cortes realizados hoje: %d\n💰 Faturamento estimado: R$ %.2f", dataHoje, totalCortes, faturamentoEstimado);
    }

    public int processAgenda(String jsonAgenda) {
        int agendados = 0;
        agendaAtiva.clear();
        agendaDisplay.clear();

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
                        Customer customer = customerService.findByName(name);
                        String phone = (customer != null) ? customer.getPhoneNumber() : null;
                        
                        Agendamento ag = new Agendamento();
                        ag.setName(name);
                        ag.setPhone(phone);
                        ag.setTime(time);
                        ag.setDate(date);
                        ag.setService(service);
                        
                        agendaAtiva.add(ag);
                        agendaDisplay.add("📅 " + date + " às ⏰ " + time + " - " + name + " | " + service);
                        if (phone != null) {
                            agendados++;
                            criarTarefasAgendadas(ag);
                        }
                    }
                }
            }
            salvarAgendaEmArquivo();
            return agendados;
        } catch (Exception e) {
            log.error("Erro Agenda JSON: {}", e.getMessage());
            throw new RuntimeException("Falha na agenda", e);
        }
    }

    // Removida a anotação @Scheduled para evitar duplicidade de mensagens (as tarefas agora rodam via banco pelo BarberBotScheduler)
    public void monitorarFluxo() {
        if (agendaAtiva.isEmpty()) return;
        LocalTime now = LocalTime.now(ZoneId.of("America/Sao_Paulo"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));

        for (Agendamento appt : agendaAtiva) {
            if (appt.getPhone() == null || (!appt.getDate().equals(dataHoje) && !appt.getDate().equalsIgnoreCase("hoje"))) continue;

            try {
                String cleanTime = appt.getTime().trim().replace("h", ":").replace(" ", "");
                if (cleanTime.length() == 4 && cleanTime.charAt(1) == ':') cleanTime = "0" + cleanTime;
                LocalTime apptTime = LocalTime.parse(cleanTime, formatter);
                
                long minutesUntil = ChronoUnit.MINUTES.between(now, apptTime);
                if (!appt.isNotified() && minutesUntil <= 61 && minutesUntil >= 59) {
                    String pNome = appt.getName().split(" ")[0];
                    whatsAppService.sendTextMessage(appt.getPhone(), "Fala, " + pNome + "! 💈\nSeu horário está chegando! Te esperamos hoje às *" + appt.getTime() + "* na Barbearia Ficticia.");
                    appt.setNotified(true);
                }

                long minutesAfter = ChronoUnit.MINUTES.between(apptTime, now);
                if (!appt.isThanked() && minutesAfter <= 45 && minutesAfter >= 35) {
                    String pNome = appt.getName().split(" ")[0];
                    whatsAppService.sendTextMessage(appt.getPhone(), "E aí, " + pNome + ", curtiu o talento? 🧔‍♂️\nSua avaliação ajuda a barbearia a crescer! Leva só 1 minuto:\n⭐ https://maps.app.goo.gl/link-ficticio-google\n\nValeu\n\nValeu demais! 👊");
                    appt.setThanked(true);
                }
            } catch (Exception e) {}
        }
    }

    public int getAgendamentosHojeCount() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        int count = 0;
        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                count++;
            }
        }
        return count;
    }

    public String getAgendaHojeText() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        List<String> list = new ArrayList<>();
        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                list.add("⏰ " + ag.getTime() + " - " + ag.getName() + " (" + ag.getService() + ")");
            }
        }
        if (list.isEmpty()) {
            return "Nenhum agendamento registrado para hoje.";
        }
        return String.join("\n", list);
    }

    public String getResumoDetelhadoDoDia() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        int totalCortes = 0;
        double faturamentoEstimado = 0.0;
        List<String> list = new ArrayList<>();

        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                totalCortes++;
                String serv = ag.getService();
                String servLower = serv.toLowerCase();
                if (servLower.contains("combo")) faturamentoEstimado += 60.0;
                else if (servLower.contains("barba")) faturamentoEstimado += 30.0;
                else faturamentoEstimado += 35.0; 
                
                String paymentBadge = "";
                if ("PIX".equalsIgnoreCase(ag.getPaymentStatus())) {
                    paymentBadge = " [PIX 💳]";
                } else if ("PRESENCIAL".equalsIgnoreCase(ag.getPaymentStatus())) {
                    paymentBadge = " [Presencial 💵]";
                } else {
                    paymentBadge = " [Pendente ⏳]";
                }
                
                list.add("✅ " + ag.getTime() + " - " + ag.getName() + " (" + serv + ")" + paymentBadge);
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Relatório de Fechamento - ").append(dataHoje).append("*\n\n");
        sb.append("✂️ *Serviços Realizados hoje:* ").append(totalCortes).append("\n");
        sb.append("💰 *Faturamento estimado:* R$ ").append(String.format("%.2f", faturamentoEstimado)).append("\n\n");
        if (list.isEmpty()) {
            sb.append("Nenhum serviço realizado hoje.");
        } else {
            sb.append("📋 *Detalhes dos Atendimentos:*\n");
            sb.append(String.join("\n", list));
        }
        return sb.toString();
    }

    public String getResumoFinanceiroDetalhado() {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        int totalCortes = 0;
        int totalCombo = 0;
        int totalBarba = 0;
        int totalSimples = 0;
        
        int pixCount = 0;
        double pixVal = 0.0;
        int presencialCount = 0;
        double presencialVal = 0.0;
        int pendenteCount = 0;
        double pendenteVal = 0.0;
        
        double faturamentoTotal = 0.0;

        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                totalCortes++;
                
                double valorServico = 35.0;
                String serv = ag.getService().toLowerCase();
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
                💰 *RESUMO FINANCEIRO DETALHADO (%s)*
                
                ✂️ *Volume de Serviços:*
                - Total de Atendimentos: %d
                - Combos (Cabelo + Barba): %d
                - Apenas Barba: %d
                - Cortes Simples / Outros: %d
                
                💳 *Fluxo de Caixa por Meio de Pagamento:*
                - ✅ *PIX (Confirmado):* R$ %.2f (%d recebimento(s))
                - 💵 *Presencial (A Receber):* R$ %.2f (%d recebimento(s))
                - ⏳ *Pendente / Outros:* R$ %.2f (%d agendamento(s))
                
                📈 *Indicadores de Performance:*
                - Faturamento Estimado Total: *R$ %.2f*
                - Ticket Médio por Cliente: *R$ %.2f*
                """, 
                dataHoje, totalCortes, totalCombo, totalBarba, totalSimples, 
                pixVal, pixCount, presencialVal, presencialCount, pendenteVal, pendenteCount,
                faturamentoTotal, ticketMedio);
    }

    public String getPainelAgendamentosDetalhado(long totalBase, long waitlistCount, long pausedCount) {
        String dataHoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM"));
        
        List<String> list = new ArrayList<>();
        int countHoje = 0;
        for (Agendamento ag : agendaAtiva) {
            if (ag.getDate().equals(dataHoje) || ag.getDate().equalsIgnoreCase("hoje")) {
                countHoje++;
                String badge = "";
                if ("PIX".equalsIgnoreCase(ag.getPaymentStatus())) badge = " 🟢 [PIX]";
                else if ("PRESENCIAL".equalsIgnoreCase(ag.getPaymentStatus())) badge = " 🔵 [Presencial]";
                else badge = " 🟡 [Pendente]";
                
                list.add(String.format("⏰ *%s* - %s (%s)%s", ag.getTime(), ag.getName(), ag.getService(), badge));
            }
        }
        
        String agendaStr = list.isEmpty() ? "_Nenhum agendamento para hoje até o momento._" : String.join("\n", list);
        
        return String.format("""
                📅 *PAINEL DE AGENDAMENTOS (%s)*
                
                👥 *Status dos Clientes & Fila:*
                - Clientes Cadastrados na Base: %d
                - Fila de Espera (Lista): %d
                - Robô Silenciado (Suporte Humano): %d cliente(s)
                
                📋 *Agendamentos do Dia (%d total):*
                %s
                """, 
                dataHoje, totalBase, waitlistCount, pausedCount, countHoje, agendaStr);
    }
}
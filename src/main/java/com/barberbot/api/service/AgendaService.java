package com.barberbot.api.service;

import com.barberbot.api.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
    }

    private static final List<Agendamento> agendaAtiva = new CopyOnWriteArrayList<>();
    private static final List<String> agendaDisplay = new CopyOnWriteArrayList<>(); 

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
                        if (phone != null) agendados++;
                    }
                }
            }
            return agendados;
        } catch (Exception e) {
            log.error("Erro Agenda JSON: {}", e.getMessage());
            throw new RuntimeException("Falha na agenda", e);
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
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
                    whatsAppService.sendTextMessage(appt.getPhone(), "Fala, " + pNome + "! 💈\nSeu horário está chegando! Te esperamos hoje às *" + appt.getTime() + "* na Barbearia Vintage.");
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
}
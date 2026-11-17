package com.razor.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Migracao legada, executada uma vez no boot: se existir um {@code agenda.json} (agenda da era
 * pre-Postgres) no diretorio de trabalho, importa os registros para {@code appointments} e
 * renomeia o arquivo para {@code agenda.json.bkp}. Sem o arquivo, nao faz nada.
 *
 * <p>Isolada aqui para que as regras de agenda nao carreguem esse codigo de transicao.
 * Extraida do antigo {@link AgendaService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyAgendaMigration {

    private final AppointmentRepository appointmentRepository;
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

    private void carregarEMigrarAgendaDeArquivo() {
        try {
            Path path = Paths.get("agenda.json");
            if (Files.exists(path)) {
                String json = Files.readString(path);
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
                Path backupPath = Paths.get("agenda.json.bkp");
                Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);

            } else {
                log.info("[AGENDA] Nenhum arquivo agenda.json encontrado para migração. Utilizando PostgreSQL.");
            }
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao carregar/migrar agenda do arquivo JSON: {}", e.getMessage());
        }
    }
}

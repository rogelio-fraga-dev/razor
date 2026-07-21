package com.razor.api.service;

import com.razor.api.dto.AppointmentView;
import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.dto.FinancialSummaryResponse.Bucket;
import com.razor.api.dto.FinancialSummaryResponse.CategoryBreakdown;
import com.razor.api.dto.FinancialSummaryResponse.PaymentBreakdown;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.PricingService.ServiceCategory;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Relatorios estruturados (JSON) do painel web: resumo financeiro por periodo e agenda do dia.
 * Reusa a categorizacao e os precos do catalogo ({@link PricingService}); sempre escopado no
 * tenant atual. Espelha os dados dos resumos de WhatsApp, mas em formato para o front.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PricingService pricingService;

    /** Resumo financeiro do intervalo [from, to] (inclusive) do tenant atual. */
    public FinancialSummaryResponse financialSummary(LocalDate from, LocalDate to) {
        UUID shop = TenantContext.getCurrent();
        Map<ServiceCategory, BigDecimal> precos = pricingService.resolvePrices();

        int combo = 0, barba = 0, simples = 0;
        int pixCount = 0, presCount = 0, pendCount = 0;
        BigDecimal pixVal = BigDecimal.ZERO, presVal = BigDecimal.ZERO, pendVal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        for (Appointment ag : appointmentRepository.findByBarbershopId(shop)) {
            LocalDate data = ag.getAppointmentDate();
            if (data == null || data.isBefore(from) || data.isAfter(to)) continue;

            ServiceCategory cat = pricingService.categorize(ag.getService());
            BigDecimal valor = precos.get(cat);
            count++;
            total = total.add(valor);
            switch (cat) {
                case COMBO -> combo++;
                case BARBA -> barba++;
                case SIMPLES -> simples++;
            }

            PaymentStatus status = ag.getPaymentStatus();
            if (status == PaymentStatus.PIX) {
                pixCount++;
                pixVal = pixVal.add(valor);
            } else if (status == PaymentStatus.PRESENCIAL) {
                presCount++;
                presVal = presVal.add(valor);
            } else {
                pendCount++;
                pendVal = pendVal.add(valor);
            }
        }

        BigDecimal averageTicket = count > 0
                ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new FinancialSummaryResponse(from, to, count,
                new CategoryBreakdown(combo, barba, simples),
                new PaymentBreakdown(
                        new Bucket(pixCount, scale(pixVal)),
                        new Bucket(presCount, scale(presVal)),
                        new Bucket(pendCount, scale(pendVal))),
                scale(total), averageTicket);
    }

    /** Agenda do dia do tenant atual, ordenada por horario, com o nome do barbeiro resolvido. */
    public List<AppointmentView> dailyAgenda(LocalDate date) {
        UUID shop = TenantContext.getCurrent();
        Map<UUID, String> nomePorProfissional = professionalRepository.findByBarbershopId(shop).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));

        return appointmentRepository.findByBarbershopId(shop).stream()
                .filter(a -> date.equals(a.getAppointmentDate()))
                .sorted(Comparator.comparing(Appointment::getAppointmentTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> new AppointmentView(
                        a.getAppointmentTime(),
                        a.getCustomerName(),
                        a.getService(),
                        a.getPaymentStatus(),
                        a.getProfessionalId() == null ? null : nomePorProfissional.get(a.getProfessionalId())))
                .toList();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

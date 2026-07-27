package com.razor.api.service;

import com.razor.api.dto.AppointmentView;
import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.dto.FinancialSummaryResponse.Bucket;
import com.razor.api.dto.FinancialSummaryResponse.CategoryBreakdown;
import com.razor.api.dto.FinancialSummaryResponse.PaymentBreakdown;
import com.razor.api.dto.RevenuePoint;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.ProductSale;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.PricingService.ServiceCategory;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final ProductSaleRepository productSaleRepository;
    private final PricingService pricingService;
    private final CustomerService customerService;

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

        // Ticket medio segue sendo servicos / atendimentos: e o valor medio do atendimento.
        // Somar produto ali inflaria o ticket com venda de balcao que nem teve atendimento.
        BigDecimal averageTicket = count > 0
                ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Vendas de produto do periodo (balcao V21 e comanda V22 gravam na mesma tabela, entao
        // somar aqui nao conta duas vezes).
        int productsSold = 0;
        BigDecimal productsRevenue = BigDecimal.ZERO;
        for (ProductSale venda : productSaleRepository.findByBarbershopIdAndSaleDateBetween(shop, from, to)) {
            productsSold += venda.getQuantity();
            productsRevenue = productsRevenue.add(venda.getTotal());
        }

        return new FinancialSummaryResponse(from, to, count,
                new CategoryBreakdown(combo, barba, simples),
                new PaymentBreakdown(
                        new Bucket(pixCount, scale(pixVal)),
                        new Bucket(presCount, scale(presVal)),
                        new Bucket(pendCount, scale(pendVal))),
                scale(total), productsSold, scale(productsRevenue),
                scale(total.add(productsRevenue)), averageTicket);
    }

    /** Periodos do grafico de faturamento do painel. O backend e quem define as faixas. */
    public enum SeriesPeriod {
        /** Os ultimos 7 dias, um ponto por dia. */
        LAST_7_DAYS,
        /** Os ultimos 30 dias em 5 faixas de 6 dias. */
        LAST_30_DAYS,
        /** Os ultimos 6 meses, um ponto por mes. */
        LAST_6_MONTHS
    }

    private static final DateTimeFormatter WEEKDAY =
            DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d/M");
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("pt-BR"));

    /**
     * Serie de faturamento do periodo, ja dividida em faixas. Uma unica varredura dos
     * agendamentos do tenant alimenta todos os pontos — antes o painel pedia um resumo por
     * ponto e cada um relia a tabela inteira.
     *
     * <p>Inclui servicos <b>e</b> produtos, para bater com o {@code totalRevenue} do resumo
     * financeiro — grafico e KPI contando coisas diferentes so confundiria o dono.
     */
    public List<RevenuePoint> revenueSeries(SeriesPeriod period, LocalDate today) {
        UUID shop = TenantContext.getCurrent();
        List<RevenuePoint> buckets = emptyBuckets(period, today);
        Map<ServiceCategory, BigDecimal> precos = pricingService.resolvePrices();
        BigDecimal[] totals = new BigDecimal[buckets.size()];
        Arrays.fill(totals, BigDecimal.ZERO);

        for (Appointment ag : appointmentRepository.findByBarbershopId(shop)) {
            LocalDate data = ag.getAppointmentDate();
            if (data == null) continue;
            addToBucket(buckets, totals, data, precos.get(pricingService.categorize(ag.getService())));
        }

        LocalDate windowStart = buckets.get(0).from();
        LocalDate windowEnd = buckets.get(buckets.size() - 1).to();
        for (ProductSale venda : productSaleRepository.findByBarbershopIdAndSaleDateBetween(shop, windowStart, windowEnd)) {
            addToBucket(buckets, totals, venda.getSaleDate(), venda.getTotal());
        }

        List<RevenuePoint> series = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) {
            RevenuePoint b = buckets.get(i);
            series.add(new RevenuePoint(b.label(), b.from(), b.to(), scale(totals[i])));
        }
        return series;
    }

    /** "ter." -> "ter": o rotulo do eixo do grafico e curto e nao leva ponto de abreviacao. */
    private String shortLabel(String formatted) {
        return formatted.replace(".", "");
    }

    /** Soma o valor na faixa que contem a data. Fora da janela do grafico, ignora. */
    private void addToBucket(List<RevenuePoint> buckets, BigDecimal[] totals, LocalDate date, BigDecimal value) {
        if (date == null || value == null) return;
        for (int i = 0; i < buckets.size(); i++) {
            RevenuePoint b = buckets.get(i);
            if (!date.isBefore(b.from()) && !date.isAfter(b.to())) {
                totals[i] = totals[i].add(value);
                return;
            }
        }
    }

    /** As faixas (rotulo + intervalo) do periodo, da mais antiga para a mais recente. */
    private List<RevenuePoint> emptyBuckets(SeriesPeriod period, LocalDate today) {
        List<RevenuePoint> buckets = new ArrayList<>();
        switch (period) {
            case LAST_7_DAYS -> {
                for (int i = 6; i >= 0; i--) {
                    LocalDate day = today.minusDays(i);
                    buckets.add(new RevenuePoint(shortLabel(WEEKDAY.format(day)), day, day, BigDecimal.ZERO));
                }
            }
            case LAST_30_DAYS -> {
                for (int i = 0; i < 5; i++) {
                    LocalDate start = today.minusDays(29L - i * 6L);
                    LocalDate end = start.plusDays(5);
                    buckets.add(new RevenuePoint(DAY_MONTH.format(start), start, end, BigDecimal.ZERO));
                }
            }
            case LAST_6_MONTHS -> {
                for (int i = 5; i >= 0; i--) {
                    LocalDate start = today.minusMonths(i).withDayOfMonth(1);
                    LocalDate end = start.plusMonths(1).minusDays(1);
                    buckets.add(new RevenuePoint(shortLabel(MONTH.format(start)), start,
                            end.isAfter(today) ? today : end, BigDecimal.ZERO));
                }
            }
        }
        return buckets;
    }

    /** Agenda do dia do tenant atual, ordenada por horario, com o nome do barbeiro resolvido. */
    public List<AppointmentView> dailyAgenda(LocalDate date) {
        UUID shop = TenantContext.getCurrent();
        Map<UUID, String> nomePorProfissional = professionalRepository.findByBarbershopId(shop).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
        Set<String> assinantes = customerService.subscriberPhones();
        Map<ServiceCategory, Integer> duracoes = pricingService.resolveDurations();

        return appointmentRepository.findByBarbershopId(shop).stream()
                .filter(a -> date.equals(a.getAppointmentDate()))
                .sorted(Comparator.comparing(Appointment::getAppointmentTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> new AppointmentView(
                        a.getAppointmentTime(),
                        a.getCustomerName(),
                        a.getCustomerPhone(),
                        a.getService(),
                        duracoes.getOrDefault(pricingService.categorize(a.getService()), 30),
                        a.getPaymentStatus(),
                        a.getProfessionalId(),
                        a.getProfessionalId() == null ? null : nomePorProfissional.get(a.getProfessionalId()),
                        a.getCustomerPhone() != null && assinantes.contains(a.getCustomerPhone())))
                .toList();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.model.ProductSale;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Comissoes por profissional (Fase 2 — paridade de gestao). Para cada agendamento atribuido a um
 * barbeiro, a comissao = valor do servico (do catalogo, via {@link PricingService}) x percentual
 * de comissao do barbeiro. Sempre escopado no tenant atual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProductSaleRepository productSaleRepository;
    private final PricingService pricingService;

    /**
     * Comissao consolidada de um profissional num periodo.
     *
     * <p>{@code faturamento} e o dos <b>servicos</b>; produtos vendidos no balcao vem separados
     * em {@code produtos}/{@code faturamentoProdutos}, porque sao coisas diferentes para o dono.
     * A {@code comissao} incide sobre os dois, usando o mesmo percentual do barbeiro — percentual
     * proprio para produto e um item futuro do roadmap.
     */
    public record ProfessionalCommission(
            UUID professionalId,
            String professionalName,
            BigDecimal commissionPercent,
            int atendimentos,
            BigDecimal faturamento,
            int produtos,
            BigDecimal faturamentoProdutos,
            BigDecimal comissao) {
    }

    public List<ProfessionalCommission> commissionsForDay(LocalDate date) {
        return commissionsForPeriod(date, date);
    }

    /**
     * Comissoes por profissional no intervalo [start, end] (inclusive). Considera apenas
     * agendamentos com barbeiro atribuido; agendamentos sem profissional sao ignorados.
     */
    public List<ProfessionalCommission> commissionsForPeriod(LocalDate start, LocalDate end) {
        UUID shop = TenantContext.getCurrent();
        Map<UUID, Professional> profs = professionalRepository.findByBarbershopId(shop).stream()
                .collect(Collectors.toMap(Professional::getId, p -> p, (a, b) -> a));
        Map<PricingService.ServiceCategory, BigDecimal> precos = pricingService.resolvePrices();

        Map<UUID, Acumulador> porProf = new LinkedHashMap<>();
        for (Appointment ag : appointmentRepository.findByBarbershopId(shop)) {
            UUID pid = ag.getProfessionalId();
            LocalDate data = ag.getAppointmentDate();
            if (pid == null || data == null) continue;
            if (data.isBefore(start) || data.isAfter(end)) continue;

            BigDecimal valor = precos.get(pricingService.categorize(ag.getService()));
            Acumulador acc = porProf.computeIfAbsent(pid, k -> new Acumulador());
            acc.atendimentos++;
            acc.faturamento = acc.faturamento.add(valor);
        }

        // Vendas de balcao atribuidas a um barbeiro (V21). Venda sem barbeiro nao gera comissao.
        for (ProductSale venda : productSaleRepository.findByBarbershopIdAndSaleDateBetween(shop, start, end)) {
            UUID pid = venda.getProfessionalId();
            if (pid == null) continue;
            Acumulador acc = porProf.computeIfAbsent(pid, k -> new Acumulador());
            acc.produtos += venda.getQuantity();
            acc.faturamentoProdutos = acc.faturamentoProdutos.add(venda.getTotal());
        }

        List<ProfessionalCommission> resultado = new ArrayList<>();
        for (Map.Entry<UUID, Acumulador> entry : porProf.entrySet()) {
            Professional p = profs.get(entry.getKey());
            String nome = p != null ? p.getName() : "Sem barbeiro";
            BigDecimal pct = (p != null && p.getCommissionPercent() != null) ? p.getCommissionPercent() : BigDecimal.ZERO;
            BigDecimal faturamento = entry.getValue().faturamento.setScale(2, RoundingMode.HALF_UP);
            BigDecimal faturamentoProdutos = entry.getValue().faturamentoProdutos.setScale(2, RoundingMode.HALF_UP);
            BigDecimal base = faturamento.add(faturamentoProdutos);
            BigDecimal comissao = base.multiply(pct).divide(CEM, 2, RoundingMode.HALF_UP);
            resultado.add(new ProfessionalCommission(entry.getKey(), nome, pct,
                    entry.getValue().atendimentos, faturamento,
                    entry.getValue().produtos, faturamentoProdutos, comissao));
        }
        resultado.sort(Comparator.comparing(ProfessionalCommission::professionalName, String.CASE_INSENSITIVE_ORDER));
        return resultado;
    }

    /** Relatorio textual das comissoes de hoje, para o painel do admin. */
    public String getComissoesDoDiaText() {
        LocalDate hoje = AppointmentDates.today();
        List<ProfessionalCommission> comissoes = commissionsForDay(hoje);
        String cabecalho = "💈 *Comissões de " + AppointmentDates.formatDate(hoje) + "*";

        if (comissoes.isEmpty()) {
            return cabecalho + "\n\n_Nenhum atendimento com barbeiro atribuído hoje._";
        }

        StringBuilder sb = new StringBuilder(cabecalho).append("\n━━━━━━━━━━━━━━━━━━\n");
        BigDecimal total = BigDecimal.ZERO;
        for (ProfessionalCommission c : comissoes) {
            sb.append(String.format("👤 *%s* — %d atend.%n", c.professionalName(), c.atendimentos()));
            sb.append(String.format("   Serviços: R$ %.2f%n", c.faturamento()));
            if (c.produtos() > 0) {
                sb.append(String.format("   Produtos: R$ %.2f (%d un.)%n",
                        c.faturamentoProdutos(), c.produtos()));
            }
            sb.append(String.format("   Comissão (%s%%): *R$ %.2f*%n",
                    formatPercent(c.commissionPercent()), c.comissao()));
            total = total.add(c.comissao());
        }
        sb.append("━━━━━━━━━━━━━━━━━━\n💰 *Total de comissões: R$ ")
                .append(String.format("%.2f", total)).append("*");
        return sb.toString();
    }

    private String formatPercent(BigDecimal pct) {
        return pct.stripTrailingZeros().toPlainString();
    }

    private static final class Acumulador {
        private int atendimentos = 0;
        private BigDecimal faturamento = BigDecimal.ZERO;
        private int produtos = 0;
        private BigDecimal faturamentoProdutos = BigDecimal.ZERO;
    }
}

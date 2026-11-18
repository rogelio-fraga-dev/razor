package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.model.ProductSale;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.PackageUsage;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.PackageUsageRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final PricingService pricingService;
    private final PackageUsageRepository packageUsageRepository;

    /**
     * Comissao consolidada de um profissional num periodo.
     *
     * <p>{@code faturamento} e o dos <b>servicos</b>; produtos vendidos vem separados em
     * {@code produtos}/{@code faturamentoProdutos}, porque sao coisas diferentes para o dono.
     * Cada base usa o <b>seu</b> percentual ({@code commissionPercent} e
     * {@code productCommissionPercent}, V23) e a {@code comissao} e a soma das duas parcelas.
     */
    public record ProfessionalCommission(
            UUID professionalId,
            String professionalName,
            BigDecimal commissionPercent,
            int atendimentos,
            BigDecimal faturamento,
            BigDecimal productCommissionPercent,
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

        // Percentual proprio de cada servico (V27), por nome — o agendamento guarda o nome.
        Map<String, BigDecimal> pctPorServico = serviceOfferingRepository.findByBarbershopId(shop).stream()
                .filter(s -> s.getCommissionPercent() != null)
                .collect(Collectors.toMap(
                        s -> s.getName().trim().toLowerCase(Locale.ROOT),
                        ServiceOffering::getCommissionPercent,
                        (a, b) -> a));

        List<Appointment> doPeriodo = appointmentRepository.findByBarbershopId(shop).stream()
                .filter(ag -> ag.getProfessionalId() != null && ag.getAppointmentDate() != null)
                .filter(ag -> !ag.getAppointmentDate().isBefore(start) && !ag.getAppointmentDate().isAfter(end))
                .toList();
        Map<Long, List<PackageUsage>> usosDePacote = packageUsagesByAppointment(shop, doPeriodo);

        Map<UUID, Acumulador> porProf = new LinkedHashMap<>();
        for (Appointment ag : doPeriodo) {
            UUID pid = ag.getProfessionalId();

            BigDecimal valor = precos.get(pricingService.categorize(ag.getService()));
            // Pacote pre-pago (V34): o que foi pago pelo pacote entra pelo valor unitario do
            // pacote (preco / usos), nao pelo preco de tabela — foi isso que entrou por ele.
            valor = applyPackageValue(valor, ag.getId() == null ? null : usosDePacote.get(ag.getId()));
            Acumulador acc = porProf.computeIfAbsent(pid, k -> new Acumulador());
            acc.atendimentos++;
            acc.faturamento = acc.faturamento.add(valor);

            // A comissao do servico e apurada aqui, atendimento a atendimento, porque cada
            // servico pode ter percentual proprio (V27). Sem percentual proprio, vale o do
            // barbeiro — o comportamento anterior.
            Professional prof = profs.get(pid);
            BigDecimal servicePct = pctPorServico.get(nomeServico(ag));
            BigDecimal pct = servicePct != null ? servicePct : percentOf(prof);
            acc.comissaoServicos = acc.comissaoServicos.add(
                    valor.multiply(pct).divide(CEM, 2, RoundingMode.HALF_UP));
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
            // Sem percentual proprio de produto, vale o de servico (V23).
            BigDecimal produtoPct = p != null ? p.effectiveProductCommissionPercent() : BigDecimal.ZERO;

            BigDecimal faturamento = entry.getValue().faturamento.setScale(2, RoundingMode.HALF_UP);
            BigDecimal faturamentoProdutos = entry.getValue().faturamentoProdutos.setScale(2, RoundingMode.HALF_UP);

            // Servicos ja vieram apurados atendimento a atendimento (cada servico pode ter o
            // seu percentual, V27). Produtos usam a taxa de produto do barbeiro (V23).
            BigDecimal comissaoServicos = entry.getValue().comissaoServicos.setScale(2, RoundingMode.HALF_UP);
            BigDecimal comissaoProdutos = faturamentoProdutos.multiply(produtoPct).divide(CEM, 2, RoundingMode.HALF_UP);

            resultado.add(new ProfessionalCommission(entry.getKey(), nome, pct,
                    entry.getValue().atendimentos, faturamento, produtoPct,
                    entry.getValue().produtos, faturamentoProdutos,
                    comissaoServicos.add(comissaoProdutos)));
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
            sb.append(String.format("   Serviços: R$ %.2f (%s%%)%n",
                    c.faturamento(), formatPercent(c.commissionPercent())));
            if (c.produtos() > 0) {
                sb.append(String.format("   Produtos: R$ %.2f (%d un., %s%%)%n",
                        c.faturamentoProdutos(), c.produtos(),
                        formatPercent(c.productCommissionPercent())));
            }
            sb.append(String.format("   Comissão: *R$ %.2f*%n", c.comissao()));
            total = total.add(c.comissao());
        }
        sb.append("━━━━━━━━━━━━━━━━━━\n💰 *Total de comissões: R$ ")
                .append(String.format("%.2f", total)).append("*");
        return sb.toString();
    }

    /** Usos de pacote dos agendamentos do periodo, agrupados por agendamento. */
    private Map<Long, List<PackageUsage>> packageUsagesByAppointment(UUID shop, List<Appointment> agendamentos) {
        List<Long> ids = agendamentos.stream().map(Appointment::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        List<PackageUsage> usos = packageUsageRepository.findByBarbershopIdAndAppointmentIdIn(shop, ids);
        if (usos == null || usos.isEmpty()) return Map.of();
        return usos.stream().collect(Collectors.groupingBy(PackageUsage::getAppointmentId));
    }

    /**
     * Troca, na base do atendimento, o preco de tabela do que o pacote pagou pelo valor unitario
     * do pacote. O resto do atendimento (ex.: a barba fora do pacote) continua pelo preco cheio.
     */
    static BigDecimal applyPackageValue(BigDecimal valor, List<PackageUsage> usos) {
        if (usos == null || usos.isEmpty()) return valor;
        BigDecimal base = valor;
        for (PackageUsage u : usos) {
            BigDecimal qtd = BigDecimal.valueOf(u.getUses());
            base = base.subtract(u.getOriginalPrice().multiply(qtd)).add(u.getUnitValue().multiply(qtd));
        }
        return base.max(BigDecimal.ZERO);
    }

    private String nomeServico(com.razor.api.model.Appointment a) {
        return a.getService() == null ? "" : a.getService().trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal percentOf(Professional p) {
        return (p != null && p.getCommissionPercent() != null) ? p.getCommissionPercent() : BigDecimal.ZERO;
    }

    private String formatPercent(BigDecimal pct) {
        return pct.stripTrailingZeros().toPlainString();
    }

    private static final class Acumulador {
        private int atendimentos = 0;
        private BigDecimal faturamento = BigDecimal.ZERO;
        private BigDecimal comissaoServicos = BigDecimal.ZERO;
        private int produtos = 0;
        private BigDecimal faturamentoProdutos = BigDecimal.ZERO;
    }
}

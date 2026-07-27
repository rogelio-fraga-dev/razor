package com.razor.api.dto;

import com.razor.api.service.CommissionService.ProfessionalCommission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Relatorio de comissoes por profissional num dia, com o total. */
public record CommissionReportResponse(LocalDate date, List<Item> items, BigDecimal total) {

    /** {@code faturamento} e o dos servicos; produtos do balcao vem separados (V21). */
    public record Item(
            UUID professionalId,
            String professionalName,
            BigDecimal commissionPercent,
            int atendimentos,
            BigDecimal faturamento,
            BigDecimal productCommissionPercent,
            int produtos,
            BigDecimal faturamentoProdutos,
            BigDecimal comissao) {

        public static Item from(ProfessionalCommission c) {
            return new Item(c.professionalId(), c.professionalName(), c.commissionPercent(),
                    c.atendimentos(), c.faturamento(), c.productCommissionPercent(),
                    c.produtos(), c.faturamentoProdutos(), c.comissao());
        }
    }

    public static CommissionReportResponse of(LocalDate date, List<ProfessionalCommission> commissions) {
        List<Item> items = commissions.stream().map(Item::from).toList();
        BigDecimal total = items.stream().map(Item::comissao).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CommissionReportResponse(date, items, total);
    }
}

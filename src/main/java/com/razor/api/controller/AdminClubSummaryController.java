package com.razor.api.controller;

import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recorrência do clube de assinatura (26/11): assinantes por plano, receita recorrente do mês
 * (soma das mensalidades) e quanto ela pesa no faturamento dos últimos 30 dias.
 * Fica sob /api/admin/plans: o plano piloto (sem clube) recebe 403 do PlanFeatureGuard.
 */
@RestController
@RequestMapping("/api/admin/plans/summary")
@RequiredArgsConstructor
public class AdminClubSummaryController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public Map<String, Object> summary() {
        UUID shop = TenantContext.getCurrent();
        List<Map<String, Object>> plans = jdbc.queryForList("""
                select p.id, p.name, p.price, count(c.id) as subscribers, p.price * count(c.id) as monthly
                  from subscription_plans p
                  left join customers c on c.subscription_plan_id = p.id and c.barbershop_id = p.barbershop_id
                 where p.barbershop_id = ? and p.active
                 group by p.id, p.name, p.price order by p.price
                """, shop);
        BigDecimal mrr = plans.stream().map(r -> (BigDecimal) r.get("monthly")).reduce(BigDecimal.ZERO, BigDecimal::add);
        long subscribers = plans.stream().mapToLong(r -> ((Number) r.get("subscribers")).longValue()).sum();
        Long customers = jdbc.queryForObject("select count(*) from customers where barbershop_id = ?", Long.class, shop);
        BigDecimal revenue30 = jdbc.queryForObject("""
                select coalesce(sum(total), 0) from comandas
                 where barbershop_id = ? and status = 'FECHADA' and comanda_date > current_date - 30
                """, BigDecimal.class, shop);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plans", plans);
        out.put("subscribers", subscribers);
        out.put("customers", customers);
        out.put("monthlyRecurring", mrr);
        out.put("yearlyRecurring", mrr.multiply(BigDecimal.valueOf(12)));
        out.put("revenueLast30", revenue30);
        BigDecimal base = revenue30.add(mrr);
        out.put("recurringShare", base.signum() == 0 ? 0 : mrr.multiply(BigDecimal.valueOf(100)).divide(base, 0, RoundingMode.HALF_UP));
        return out;
    }
}

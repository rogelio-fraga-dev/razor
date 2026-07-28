package com.razor.api.controller;

import com.razor.api.dto.PageResponse;
import com.razor.api.dto.CommissionPaymentRequest;
import com.razor.api.dto.CommissionPaymentResponse;
import com.razor.api.dto.CommissionSettlementResponse;
import com.razor.api.model.CommissionPayment;
import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.AuthService;
import com.razor.api.service.CommissionPaymentService;
import com.razor.api.service.CommissionService;
import com.razor.api.service.ProfessionalAdvanceService;
import com.razor.api.service.CommissionService.ProfessionalCommission;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Acerto de comissao com o barbeiro (V25) no painel web (tenant vem do JWT): a apuracao do
 * periodo com o que ja foi pago, o registro do pagamento e o historico.
 */
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/admin/commission-payments")
@RequiredArgsConstructor
public class AdminCommissionPaymentController {

    private final CommissionPaymentService commissionPaymentService;
    private final CommissionService commissionService;
    private final ProfessionalAdvanceService advanceService;
    private final ProfessionalRepository professionalRepository;
    private final AuthService authService;

    /**
     * O que cada barbeiro apurou no periodo, o que ja recebeu e o saldo — a tela que o dono
     * olha antes de pagar. Padrao: o mes corrente ate hoje.
     */
    @GetMapping("/settlement")
    public CommissionSettlementResponse settlement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = AppointmentDates.today();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;

        List<ProfessionalCommission> apurado = commissionService.commissionsForPeriod(start, end);
        Map<UUID, BigDecimal> pagoPorProf = commissionPaymentService.paidByProfessional(start, end);
        Map<UUID, BigDecimal> valePorProf = advanceService.advancedByProfessional(start, end);

        List<CommissionSettlementResponse.Item> items = new ArrayList<>();
        BigDecimal totalComissao = BigDecimal.ZERO;
        BigDecimal totalPago = BigDecimal.ZERO;
        BigDecimal totalVales = BigDecimal.ZERO;
        BigDecimal totalAReceber = BigDecimal.ZERO;

        for (ProfessionalCommission c : apurado) {
            BigDecimal pago = pagoPorProf.getOrDefault(c.professionalId(), BigDecimal.ZERO);
            BigDecimal vales = valePorProf.getOrDefault(c.professionalId(), BigDecimal.ZERO);
            // Pode ficar negativo quando o vale passa da comissao: o barbeiro deve a barbearia,
            // e zerar o numero esconderia isso.
            BigDecimal aReceber = c.comissao().subtract(pago).subtract(vales);
            items.add(new CommissionSettlementResponse.Item(
                    c.professionalId(), c.professionalName(), c.atendimentos(), c.faturamento(),
                    c.produtos(), c.faturamentoProdutos(), c.comissao(), pago, vales, aReceber));
            totalComissao = totalComissao.add(c.comissao());
            totalPago = totalPago.add(pago);
            totalVales = totalVales.add(vales);
            totalAReceber = totalAReceber.add(aReceber);
        }

        return new CommissionSettlementResponse(
                start, end, items, totalComissao, totalPago, totalVales, totalAReceber);
    }

    /** Historico de acertos, do mais recente para o mais antigo. */
    @GetMapping
    public PageResponse<CommissionPaymentResponse> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Map<UUID, String> names = professionalNames();
        return PageResponse.of(
                commissionPaymentService.page(
                        PageResponse.pageable(page, size, Sort.by(Sort.Order.desc("paidAt")))),
                p -> CommissionPaymentResponse.from(p, names.get(p.getProfessionalId())));
    }

    @PostMapping
    public ResponseEntity<CommissionPaymentResponse> pay(
            @AuthenticationPrincipal TenantUserPrincipal principal,
            @Valid @RequestBody CommissionPaymentRequest req) {
        CommissionPayment saved = commissionPaymentService.pay(
                req.professionalId(), req.periodStart(), req.periodEnd(), req.amount(),
                Boolean.TRUE.equals(req.fromCashRegister()), req.notes(), nameOf(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommissionPaymentResponse.from(saved, professionalNames().get(saved.getProfessionalId())));
    }

    private Map<UUID, String> professionalNames() {
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(Professional::getId, Professional::getName, (a, b) -> a));
    }

    /** Nome de quem pagou; cai no e-mail do token se nao houver nome cadastrado. */
    private String nameOf(TenantUserPrincipal principal) {
        if (principal == null) return null;
        try {
            String name = authService.getUser(principal.userId()).getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception e) {
            // Cai no e-mail do token, mas com rastro: engolir sem log tornaria "usuario sem nome"
            // indistinguivel de "banco fora do ar" num registro de quem mexeu em dinheiro.
            log.warn("[AUTH] Nao foi possivel resolver o nome do usuario {}: {}",
                    principal.userId(), e.toString());
        }
        return principal.email();
    }
}

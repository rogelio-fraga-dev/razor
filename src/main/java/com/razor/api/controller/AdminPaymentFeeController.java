package com.razor.api.controller;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.service.PaymentFeeService;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Taxas por forma de pagamento (V35), ex.: {@code {"CREDITO": 3.5, "DEBITO": 1.99}}. So o dono
 * (cai na regra geral de /api/admin do {@code SecurityConfig}): taxa mexe no liquido dos
 * relatorios financeiros. Dinheiro nao aceita taxa.
 */
@RestController
@RequestMapping("/api/admin/payment-fees")
@RequiredArgsConstructor
public class AdminPaymentFeeController {

    private final PaymentFeeService paymentFeeService;

    @GetMapping
    public Map<CashPaymentMethod, BigDecimal> get() {
        return paymentFeeService.rates();
    }

    /** Grava as taxas informadas; formas ausentes ficam como estao, zero remove a taxa. */
    @PutMapping
    public Map<CashPaymentMethod, BigDecimal> update(@RequestBody Map<CashPaymentMethod, BigDecimal> rates) {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma taxa.");
        }
        return paymentFeeService.update(rates);
    }
}

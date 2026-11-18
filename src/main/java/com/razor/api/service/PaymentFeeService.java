package com.razor.api.service;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.PaymentMethodFee;
import com.razor.api.repository.PaymentMethodFeeRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Taxas por forma de pagamento (V35): quanto a maquininha (ou o banco, no PIX) fica de cada
 * venda. Configurado pelo dono; aplicado pelo {@link CashRegisterService} a cada VENDA, que grava
 * o valor da taxa no próprio movimento. Sem taxa cadastrada, a taxa é zero.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFeeService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final PaymentMethodFeeRepository repository;

    /** Taxa (%) de cada forma que aceita taxa, na ordem do enum. Sem cadastro = 0. */
    public Map<CashPaymentMethod, BigDecimal> rates() {
        UUID shop = TenantContext.getCurrent();
        Map<CashPaymentMethod, BigDecimal> out = new LinkedHashMap<>();
        Arrays.stream(CashPaymentMethod.values()).filter(CashPaymentMethod::acceptsFee)
                .forEach(m -> out.put(m, BigDecimal.ZERO.setScale(2)));
        repository.findByBarbershopId(shop).forEach(f -> out.put(f.getPaymentMethod(), f.getFeePercent()));
        return out;
    }

    /**
     * Grava as taxas informadas (as ausentes ficam como estão). Taxa zero apaga o cadastro — é o
     * mesmo que não ter.
     */
    @Transactional
    public Map<CashPaymentMethod, BigDecimal> update(Map<CashPaymentMethod, BigDecimal> rates) {
        UUID shop = TenantContext.getCurrent();
        Map<CashPaymentMethod, BigDecimal> validated = new EnumMap<>(CashPaymentMethod.class);
        rates.forEach((method, percent) -> {
            if (method == null) return;
            if (!method.acceptsFee()) {
                throw new IllegalArgumentException("Dinheiro não tem taxa: entra inteiro na gaveta.");
            }
            if (percent == null || percent.signum() < 0 || percent.compareTo(CEM) >= 0) {
                throw new IllegalArgumentException("A taxa deve estar entre 0% e 99,99%.");
            }
            validated.put(method, percent.setScale(2, RoundingMode.HALF_UP));
        });

        validated.forEach((method, percent) -> {
            var existing = repository.findByBarbershopIdAndPaymentMethod(shop, method);
            if (percent.signum() == 0) {
                existing.ifPresent(repository::delete);
            } else {
                PaymentMethodFee fee = existing.orElseGet(() -> PaymentMethodFee.builder()
                        .barbershopId(shop).paymentMethod(method).build());
                fee.setFeePercent(percent);
                repository.save(fee);
            }
        });
        log.info("[TAXAS] Atualizadas no tenant {}: {}", shop, validated);
        return rates();
    }

    /** Valor da taxa de uma venda: {@code amount × taxa%}, arredondado ao centavo. */
    public static BigDecimal feeOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null || percent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.multiply(percent).divide(CEM, 2, RoundingMode.HALF_UP);
    }
}

package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.CustomerPackage;
import com.razor.api.model.PackageUsage;
import com.razor.api.model.ServicePackage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Payloads e respostas dos pacotes pre-pagos (V34). */
public final class PackageDtos {

    private PackageDtos() {}

    /** Criacao/edicao do pacote no catalogo. {@code serviceId} nulo = qualquer servico. */
    public record PackageRequest(
            @NotBlank(message = "Informe o nome do pacote.") String name,
            UUID serviceId,
            @NotNull(message = "Informe a quantidade de usos.")
                    @Positive(message = "A quantidade de usos deve ser maior que zero.") Integer quantity,
            @NotNull(message = "Informe o preco do pacote.")
                    @Positive(message = "O preco do pacote deve ser maior que zero.") BigDecimal price,
            @NotNull(message = "Informe a validade em dias.")
                    @Positive(message = "A validade deve ser de pelo menos 1 dia.") Integer validityDays) {}

    /** Venda a um cliente. O preco nao vem do painel: quem manda e o catalogo. */
    public record SellRequest(
            @NotNull(message = "Escolha o cliente.") UUID customerId,
            @NotNull(message = "Escolha a forma de pagamento da venda.") CashPaymentMethod paymentMethod) {}

    public record PackageResponse(
            UUID id,
            String name,
            UUID serviceId,
            String serviceName,
            int quantity,
            BigDecimal price,
            BigDecimal unitValue,
            int validityDays,
            boolean active) {

        public static PackageResponse from(ServicePackage p, String serviceName) {
            return new PackageResponse(p.getId(), p.getName(), p.getServiceId(), serviceName,
                    p.getQuantity(), p.getPrice(),
                    p.getPrice().divide(BigDecimal.valueOf(p.getQuantity()), 2, java.math.RoundingMode.HALF_UP),
                    p.getValidityDays(), p.isActive());
        }
    }

    /** Saldo de um pacote vendido. {@code status}: ATIVO, ESGOTADO ou VENCIDO. */
    public record CustomerPackageResponse(
            UUID id,
            UUID customerId,
            String customerName,
            String customerPhone,
            UUID packageId,
            String packageName,
            UUID serviceId,
            String serviceName,
            int totalUses,
            int remainingUses,
            BigDecimal price,
            BigDecimal unitValue,
            CashPaymentMethod paymentMethod,
            LocalDateTime purchasedAt,
            LocalDate expiresAt,
            String status) {

        public static CustomerPackageResponse from(CustomerPackage cp, String customerName,
                                                   String customerPhone, LocalDate today) {
            String status = cp.isExpiredOn(today) ? "VENCIDO"
                    : cp.getRemainingUses() <= 0 ? "ESGOTADO" : "ATIVO";
            return new CustomerPackageResponse(cp.getId(), cp.getCustomerId(), customerName, customerPhone,
                    cp.getPackageId(), cp.getPackageName(), cp.getServiceId(), cp.getServiceName(),
                    cp.getTotalUses(), cp.getRemainingUses(), cp.getPrice(), cp.getUnitValue(),
                    cp.getPaymentMethod(), cp.getPurchasedAt(), cp.getExpiresAt(), status);
        }
    }

    public record UsageResponse(
            UUID id,
            UUID comandaId,
            String serviceName,
            int uses,
            BigDecimal unitValue,
            BigDecimal originalPrice,
            LocalDate usageDate,
            LocalDateTime usedAt) {

        public static UsageResponse from(PackageUsage u) {
            return new UsageResponse(u.getId(), u.getComandaId(), u.getServiceName(), u.getUses(),
                    u.getUnitValue(), u.getOriginalPrice(), u.getUsageDate(), u.getUsedAt());
        }
    }
}

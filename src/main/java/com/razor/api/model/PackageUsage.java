package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uma baixa de pacote (V34). Mapeia {@code package_usages}. E a historia auditavel do saldo:
 * cada servico pago pelo pacote numa comanda vira uma linha, unica por item de comanda.
 */
@Entity
@Table(name = "package_usages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "customer_package_id", nullable = false)
    private UUID customerPackageId;

    @Column(name = "comanda_id")
    private UUID comandaId;

    @Column(name = "comanda_item_id")
    private UUID comandaItemId;

    /** Agendamento da comanda (V28), quando houver — e por ele que a comissao acha o uso. */
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private Integer uses;

    /** Valor de cada uso no pacote (preco / usos), copiado do pacote do cliente. */
    @Column(name = "unit_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitValue;

    /** Preco de tabela (por unidade) que deixou de ser cobrado na comanda. */
    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
}

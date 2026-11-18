package com.razor.api.controller;

import com.razor.api.dto.PackageDtos;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.service.PackageService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saldo de pacotes visto pela tela de Clientes (V34). Fica sob {@code /api/admin/customers/**}
 * de proposito: a recepcao enxerga clientes e precisa saber, no balcao, se o cliente tem corte
 * pago — mas vender e configurar pacote continua so do dono ({@link AdminPackageController}).
 */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerPackageController {

    private final PackageService packageService;
    private final CustomerRepository customerRepository;

    /** Pacotes usaveis hoje de toda a base — a tela marca quem tem saldo. */
    @GetMapping("/package-balances")
    public List<PackageDtos.CustomerPackageResponse> usableBalances() {
        return AdminPackageController.toBalances(packageService.balances(true), customerRepository);
    }

    /** Todos os pacotes de um cliente (ativos, esgotados e vencidos). */
    @GetMapping("/{customerId}/packages")
    public List<PackageDtos.CustomerPackageResponse> ofCustomer(@PathVariable UUID customerId) {
        return AdminPackageController.toBalances(packageService.ofCustomer(customerId), customerRepository);
    }
}

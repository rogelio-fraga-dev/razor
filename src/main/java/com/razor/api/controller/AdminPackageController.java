package com.razor.api.controller;

import com.razor.api.dto.PackageDtos;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerPackage;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.ServicePackage;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.service.PackageService;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Pacotes pre-pagos no painel (V34): catalogo, venda ao cliente e saldos. So o dono (regra
 * geral de {@code /api/admin/**}) — a venda mexe no caixa e o catalogo define preco.
 * O saldo de leitura que a recepcao precisa ver mora em {@link AdminCustomerPackageController}.
 */
@RestController
@RequestMapping("/api/admin/packages")
@RequiredArgsConstructor
public class AdminPackageController {

    private final PackageService packageService;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final CustomerRepository customerRepository;

    @GetMapping
    public List<PackageDtos.PackageResponse> list() {
        Map<UUID, String> services = serviceNames();
        return packageService.listCatalog().stream().map(p -> toResponse(p, services)).toList();
    }

    @PostMapping
    public ResponseEntity<PackageDtos.PackageResponse> create(@Valid @RequestBody PackageDtos.PackageRequest req) {
        ServicePackage created = packageService.create(
                req.name(), req.serviceId(), req.quantity(), req.price(), req.validityDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created, serviceNames()));
    }

    @PutMapping("/{id}")
    public PackageDtos.PackageResponse update(@PathVariable UUID id,
                                              @Valid @RequestBody PackageDtos.PackageRequest req) {
        ServicePackage updated = packageService.update(
                id, req.name(), req.serviceId(), req.quantity(), req.price(), req.validityDays());
        return toResponse(updated, serviceNames());
    }

    /** Liga/desliga sem apagar — pacotes vendidos continuam apontando para ele. */
    @PutMapping("/{id}/active")
    public PackageDtos.PackageResponse setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return toResponse(packageService.setActive(id, active), serviceNames());
    }

    /** Vende o pacote a um cliente; exige caixa aberto. */
    @PostMapping("/{id}/sell")
    public ResponseEntity<PackageDtos.CustomerPackageResponse> sell(
            @PathVariable UUID id, @Valid @RequestBody PackageDtos.SellRequest req) {
        CustomerPackage sold = packageService.sell(id, req.customerId(), req.paymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toBalances(List.of(sold), customerRepository).get(0));
    }

    /** Pacotes vendidos: so os usaveis (padrao) ou todos com {@code ?all=true}. */
    @GetMapping("/balances")
    public List<PackageDtos.CustomerPackageResponse> balances(@RequestParam(defaultValue = "false") boolean all) {
        return toBalances(packageService.balances(!all), customerRepository);
    }

    /** Historico de baixas de um pacote vendido. */
    @GetMapping("/balances/{customerPackageId}/usages")
    public List<PackageDtos.UsageResponse> usages(@PathVariable UUID customerPackageId) {
        return packageService.usages(customerPackageId).stream().map(PackageDtos.UsageResponse::from).toList();
    }

    /** Resolve nome e telefone dos clientes de uma vez so, em vez de uma consulta por linha. */
    static List<PackageDtos.CustomerPackageResponse> toBalances(List<CustomerPackage> packages,
                                                               CustomerRepository customerRepository) {
        List<UUID> ids = packages.stream().map(CustomerPackage::getCustomerId).distinct().toList();
        Map<UUID, Customer> customers = customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a));
        LocalDate today = AppointmentDates.today();
        return packages.stream().map(cp -> {
            Customer c = customers.get(cp.getCustomerId());
            return PackageDtos.CustomerPackageResponse.from(cp,
                    c != null ? c.getName() : null, c != null ? c.getPhoneNumber() : null, today);
        }).toList();
    }

    private Map<UUID, String> serviceNames() {
        return serviceOfferingRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(ServiceOffering::getId, ServiceOffering::getName, (a, b) -> a));
    }

    private PackageDtos.PackageResponse toResponse(ServicePackage p, Map<UUID, String> services) {
        return PackageDtos.PackageResponse.from(p, p.getServiceId() == null ? null : services.get(p.getServiceId()));
    }
}

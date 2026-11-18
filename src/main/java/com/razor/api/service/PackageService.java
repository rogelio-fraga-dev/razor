package com.razor.api.service;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerPackage;
import com.razor.api.model.PackageUsage;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.ServicePackage;
import com.razor.api.repository.ComandaItemRepository;
import com.razor.api.repository.CustomerPackageRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.PackageUsageRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.ServicePackageRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.PhoneNumbers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pacotes pre-pagos (V34): catalogo, venda ao cliente e <b>baixa automatica</b> no fechamento
 * da comanda.
 *
 * <p><b>Venda.</b> Igual a venda de balcao (V21): entra no caixa <b>aberto</b> como movimento
 * de venda — sem caixa aberto, a venda e recusada, senao o dinheiro nao apareceria na
 * conferencia da gaveta. Nome, servico, preco e validade sao congelados no pacote do cliente.
 *
 * <p><b>Baixa.</b> Ao fechar uma comanda com telefone de um cliente que tem pacote usavel (com
 * saldo e dentro da validade), cada servico coberto sai a R$ 0,00 e consome um uso. Cada baixa
 * grava uma linha em {@code package_usages}, unica por item de comanda — reprocessar nunca
 * desconta duas vezes.
 *
 * <p><b>Comissao.</b> O barbeiro continua ganhando comissao sobre o servico feito com pacote,
 * mas a base passa a ser o <b>valor unitario do pacote</b> (preco / usos), e nao o preco de
 * tabela: foi isso que de fato entrou por aquele corte. Ver {@link CommissionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageService {

    /** Sufixo que marca na comanda o servico pago pelo pacote. */
    static final String PACKAGE_SUFFIX = " (pacote)";

    private final ServicePackageRepository packageRepository;
    private final CustomerPackageRepository customerPackageRepository;
    private final PackageUsageRepository usageRepository;
    private final CustomerRepository customerRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ComandaItemRepository comandaItemRepository;
    private final CashRegisterService cashRegisterService;

    // ---------- catalogo ----------

    public List<ServicePackage> listCatalog() {
        return packageRepository.findByBarbershopIdOrderByNameAsc(TenantContext.getCurrent());
    }

    public ServicePackage getCatalog(UUID id) {
        return packageRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Pacote nao encontrado."));
    }

    @Transactional
    public ServicePackage create(String name, UUID serviceId, Integer quantity, BigDecimal price,
                                 Integer validityDays) {
        UUID shop = TenantContext.getCurrent();
        String cleanName = requireName(name);
        validateNumbers(quantity, price, validityDays);
        requireServiceOfThisShop(serviceId, shop);
        requireUniqueName(shop, cleanName, null);

        ServicePackage saved = packageRepository.save(ServicePackage.builder()
                .barbershopId(shop)
                .name(cleanName)
                .serviceId(serviceId)
                .quantity(quantity)
                .price(scale(price))
                .validityDays(validityDays)
                .active(true)
                .build());
        log.info("[PACOTE] Criado '{}' no tenant {}", cleanName, shop);
        return saved;
    }

    /** Edita o catalogo. Pacotes ja vendidos nao mudam — tem as proprias copias. */
    @Transactional
    public ServicePackage update(UUID id, String name, UUID serviceId, Integer quantity, BigDecimal price,
                                 Integer validityDays) {
        ServicePackage pkg = getCatalog(id);
        String cleanName = requireName(name);
        validateNumbers(quantity, price, validityDays);
        requireServiceOfThisShop(serviceId, pkg.getBarbershopId());
        requireUniqueName(pkg.getBarbershopId(), cleanName, id);

        pkg.setName(cleanName);
        pkg.setServiceId(serviceId);
        pkg.setQuantity(quantity);
        pkg.setPrice(scale(price));
        pkg.setValidityDays(validityDays);
        return packageRepository.save(pkg);
    }

    /** Desativa em vez de excluir: o pacote vendido continua apontando para ele. */
    @Transactional
    public ServicePackage setActive(UUID id, boolean active) {
        ServicePackage pkg = getCatalog(id);
        pkg.setActive(active);
        return packageRepository.save(pkg);
    }

    // ---------- venda ----------

    /**
     * Vende um pacote a um cliente. Lanca no caixa aberto (sem caixa, recusa) e congela nome,
     * servico, preco e validade.
     */
    @Transactional
    public CustomerPackage sell(UUID packageId, UUID customerId, CashPaymentMethod method) {
        UUID shop = TenantContext.getCurrent();
        if (method == null) {
            throw new IllegalArgumentException("Escolha a forma de pagamento da venda.");
        }
        ServicePackage pkg = getCatalog(packageId);
        if (!pkg.isActive()) {
            throw new IllegalArgumentException("O pacote \"" + pkg.getName() + "\" esta inativo.");
        }
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> shop.equals(c.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Cliente nao encontrado."));

        String serviceName = null;
        if (pkg.getServiceId() != null) {
            serviceName = serviceOfferingRepository.findById(pkg.getServiceId())
                    .map(ServiceOffering::getName).orElse(null);
        }

        String customerLabel = customer.getName() != null && !customer.getName().isBlank()
                ? customer.getName() : customer.getPhoneNumber();
        // Dinheiro primeiro: sem caixa aberto, isto lanca e nada e gravado.
        CashMovement movement = cashRegisterService.recordMovement(CashMovementType.VENDA, method,
                pkg.getPrice(), "Pacote " + pkg.getName() + " — " + customerLabel);

        LocalDate today = AppointmentDates.today();
        CustomerPackage sold = customerPackageRepository.save(CustomerPackage.builder()
                .barbershopId(shop)
                .customerId(customer.getId())
                .packageId(pkg.getId())
                .packageName(pkg.getName())
                .serviceId(pkg.getServiceId())
                .serviceName(serviceName)
                .totalUses(pkg.getQuantity())
                .remainingUses(pkg.getQuantity())
                .price(pkg.getPrice())
                .unitValue(unitValue(pkg.getPrice(), pkg.getQuantity()))
                .paymentMethod(method)
                .cashMovementId(movement.getId())
                .purchasedAt(LocalDateTime.now())
                .expiresAt(today.plusDays(pkg.getValidityDays()))
                .build());
        log.info("[PACOTE] Vendido '{}' ao cliente {} por {} no tenant {}",
                pkg.getName(), customer.getId(), pkg.getPrice(), shop);
        return sold;
    }

    // ---------- saldos ----------

    /** Pacotes vendidos do tenant (todos, ou so os usaveis hoje). */
    public List<CustomerPackage> balances(boolean onlyUsable) {
        UUID shop = TenantContext.getCurrent();
        if (onlyUsable) {
            return customerPackageRepository.findUsable(shop, AppointmentDates.today()).stream()
                    .sorted(Comparator.comparing(CustomerPackage::getExpiresAt))
                    .toList();
        }
        return customerPackageRepository.findByBarbershopIdOrderByPurchasedAtDesc(shop);
    }

    public List<CustomerPackage> ofCustomer(UUID customerId) {
        return customerPackageRepository.findByBarbershopIdAndCustomerIdOrderByPurchasedAtDesc(
                TenantContext.getCurrent(), customerId);
    }

    /** Pacotes que o cliente pode usar hoje — o que o bot menciona na conversa. */
    public List<CustomerPackage> usableFor(Customer customer) {
        if (customer == null || customer.getId() == null) return List.of();
        LocalDate today = AppointmentDates.today();
        return ofCustomer(customer.getId()).stream().filter(cp -> cp.isUsableOn(today)).toList();
    }

    public List<PackageUsage> usages(UUID customerPackageId) {
        customerPackageRepository.findById(customerPackageId)
                .filter(cp -> TenantContext.getCurrent().equals(cp.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Pacote do cliente nao encontrado."));
        return usageRepository.findByCustomerPackageIdOrderByUsedAtDesc(customerPackageId);
    }

    // ---------- baixa automatica ----------

    /**
     * Aplica os pacotes do cliente da comanda aos servicos dela, <b>antes</b> do total ser
     * calculado. Cada servico coberto sai a R$ 0,00 e consome um uso por unidade.
     *
     * <p>Item com quantidade maior que o saldo e dividido: a parte coberta vira um item zerado
     * e o resto continua cobrado normalmente. Roda dentro da transacao do fechamento — se o
     * caixa recusar depois, a baixa volta junto.
     *
     * @return os itens da comanda depois da baixa (o que deve ser somado)
     */
    @Transactional
    public List<ComandaItem> consumeForComanda(Comanda comanda, List<ComandaItem> items) {
        if (comanda.getCustomerPhone() == null || comanda.getCustomerPhone().isBlank()) return items;
        if (items.stream().noneMatch(i -> i.getKind() == ComandaItem.Kind.SERVICO)) return items;

        UUID shop = comanda.getBarbershopId();
        LocalDate today = AppointmentDates.today();
        List<CustomerPackage> packages = lockPackagesOfPhone(shop, comanda.getCustomerPhone(), today);
        if (packages.isEmpty()) return items;

        List<ComandaItem> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ComandaItem item : items) {
            result.add(item);
            if (item.getKind() != ComandaItem.Kind.SERVICO) continue;
            if (item.getUnitPrice().signum() == 0) continue; // ja zerado: nada a cobrir
            if (item.getId() != null && usageRepository.existsByComandaItemId(item.getId())) continue;

            int index = result.size() - 1;
            ComandaItem current = item;
            while (current != null && current.getQuantity() > 0) {
                CustomerPackage pkg = pickPackage(packages, current.getReferenceId(), today);
                if (pkg == null) break;

                int covered = Math.min(current.getQuantity(), pkg.getRemainingUses());
                BigDecimal originalPrice = current.getUnitPrice();
                ComandaItem coveredItem;
                if (covered == current.getQuantity()) {
                    // O item inteiro e pago pelo pacote.
                    current.setUnitPrice(BigDecimal.ZERO);
                    current.setTotal(BigDecimal.ZERO);
                    current.setDescription(current.getDescription() + PACKAGE_SUFFIX);
                    coveredItem = comandaItemRepository.save(current);
                    result.set(index, coveredItem);
                    current = null;
                } else {
                    // Saldo menor que a quantidade: o resto continua cobrado.
                    int rest = current.getQuantity() - covered;
                    current.setQuantity(rest);
                    current.setTotal(originalPrice.multiply(BigDecimal.valueOf(rest)));
                    comandaItemRepository.save(current);
                    coveredItem = comandaItemRepository.save(ComandaItem.builder()
                            .comandaId(current.getComandaId())
                            .barbershopId(current.getBarbershopId())
                            .kind(ComandaItem.Kind.SERVICO)
                            .referenceId(current.getReferenceId())
                            .description(stripSuffix(current.getDescription()) + PACKAGE_SUFFIX)
                            .unitPrice(BigDecimal.ZERO)
                            .quantity(covered)
                            .total(BigDecimal.ZERO)
                            .build());
                    result.add(coveredItem);
                }

                pkg.setRemainingUses(pkg.getRemainingUses() - covered);
                customerPackageRepository.save(pkg);
                usageRepository.save(PackageUsage.builder()
                        .barbershopId(shop)
                        .customerPackageId(pkg.getId())
                        .comandaId(comanda.getId())
                        .comandaItemId(coveredItem.getId())
                        .appointmentId(comanda.getAppointmentId())
                        .professionalId(comanda.getProfessionalId())
                        .serviceName(stripSuffix(coveredItem.getDescription()))
                        .uses(covered)
                        .unitValue(pkg.getUnitValue())
                        .originalPrice(originalPrice)
                        .usageDate(comanda.getComandaDate() != null ? comanda.getComandaDate() : today)
                        .usedAt(now)
                        .build());
                log.info("[PACOTE] Baixa de {} uso(s) do pacote {} na comanda {} (restam {})",
                        covered, pkg.getId(), comanda.getId(), pkg.getRemainingUses());
            }
        }
        return result;
    }

    // ---------- apoio ----------

    /**
     * Pacotes usaveis do cliente dono do telefone, travados para escrita. O telefone e
     * comparado normalizado (com/sem DDI, com/sem o nono digito), igual ao resto do sistema.
     */
    private List<CustomerPackage> lockPackagesOfPhone(UUID shop, String phone, LocalDate today) {
        String target = PhoneNumbers.normalizeBrazilian(phone);
        if (target.isEmpty()) return List.of();

        Set<UUID> withPackage = customerPackageRepository.findUsable(shop, today).stream()
                .map(CustomerPackage::getCustomerId)
                .collect(Collectors.toSet());
        if (withPackage.isEmpty()) return List.of();

        Set<UUID> owners = customerRepository.findAllById(withPackage).stream()
                .filter(c -> shop.equals(c.getBarbershopId()))
                .filter(c -> target.equals(PhoneNumbers.normalizeBrazilian(c.getPhoneNumber())))
                .map(Customer::getId)
                .collect(Collectors.toSet());
        if (owners.isEmpty()) return List.of();

        return new ArrayList<>(customerPackageRepository.lockUsableForCustomers(shop, owners, today));
    }

    /**
     * Qual pacote paga este servico: primeiro o especifico daquele servico, depois o "qualquer
     * servico"; dentro de cada grupo, o que vence primeiro.
     */
    private CustomerPackage pickPackage(List<CustomerPackage> packages, UUID serviceId, LocalDate today) {
        Comparator<CustomerPackage> order = Comparator
                .comparing((CustomerPackage cp) -> cp.getServiceId() == null ? 1 : 0)
                .thenComparing(CustomerPackage::getExpiresAt);
        return packages.stream()
                .filter(cp -> cp.isUsableOn(today))
                .filter(cp -> cp.getServiceId() == null || cp.getServiceId().equals(serviceId))
                .min(order)
                .orElse(null);
    }

    static BigDecimal unitValue(BigDecimal price, int quantity) {
        return price.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    private String stripSuffix(String description) {
        return description != null && description.endsWith(PACKAGE_SUFFIX)
                ? description.substring(0, description.length() - PACKAGE_SUFFIX.length())
                : description;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do pacote.");
        }
        String clean = name.trim();
        if (clean.length() > 120) {
            throw new IllegalArgumentException("O nome do pacote pode ter no maximo 120 caracteres.");
        }
        return clean;
    }

    private void validateNumbers(Integer quantity, BigDecimal price, Integer validityDays) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("A quantidade de usos deve ser maior que zero.");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("O preco do pacote deve ser maior que zero.");
        }
        if (validityDays == null || validityDays <= 0) {
            throw new IllegalArgumentException("A validade deve ser de pelo menos 1 dia.");
        }
    }

    private void requireServiceOfThisShop(UUID serviceId, UUID shop) {
        if (serviceId == null) return;
        serviceOfferingRepository.findById(serviceId)
                .filter(s -> shop.equals(s.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Servico nao encontrado."));
    }

    private void requireUniqueName(UUID shop, String name, UUID selfId) {
        packageRepository.findByBarbershopIdAndNameIgnoreCase(shop, name)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ja existe um pacote chamado \"" + name + "\".");
                });
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

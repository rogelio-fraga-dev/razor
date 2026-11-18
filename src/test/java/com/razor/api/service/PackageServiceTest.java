package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerPackage;
import com.razor.api.model.PackageUsage;
import com.razor.api.model.ServicePackage;
import com.razor.api.repository.ComandaItemRepository;
import com.razor.api.repository.CustomerPackageRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.PackageUsageRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.ServicePackageRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Regras dos pacotes pre-pagos (V34): venda so com caixa aberto, preco congelado, e baixa que
 * respeita validade, saldo e nunca desconta duas vezes o mesmo item.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackageServiceTest {

    @Mock private ServicePackageRepository packageRepository;
    @Mock private CustomerPackageRepository customerPackageRepository;
    @Mock private PackageUsageRepository usageRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private ComandaItemRepository comandaItemRepository;
    @Mock private CashRegisterService cashRegisterService;

    @InjectMocks private PackageService service;

    private final UUID shop = TenantContext.DEFAULT_BARBERSHOP_ID;
    private final UUID corteId = UUID.randomUUID();
    private final UUID barbaId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final LocalDate today = AppointmentDates.today();

    @BeforeEach
    void setUp() {
        when(comandaItemRepository.save(any(ComandaItem.class))).thenAnswer(inv -> {
            ComandaItem i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(customerPackageRepository.save(any(CustomerPackage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usageRepository.save(any(PackageUsage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByBarbershopIdAndPhoneNumberIn(any(), anyCollection())).thenReturn(List.of(customer("5511988887777")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Customer customer(String phone) {
        return Customer.builder().id(customerId).barbershopId(shop).phoneNumber(phone).name("João").build();
    }

    private ServicePackage catalog(boolean active) {
        return ServicePackage.builder().id(UUID.randomUUID()).barbershopId(shop).name("10 cortes")
                .serviceId(corteId).quantity(10).price(new BigDecimal("300.00")).validityDays(90)
                .active(active).build();
    }

    private CustomerPackage pkg(UUID serviceId, int remaining, LocalDate expiresAt) {
        return CustomerPackage.builder().id(UUID.randomUUID()).barbershopId(shop).customerId(customerId)
                .packageName("10 cortes").serviceId(serviceId).totalUses(10).remainingUses(remaining)
                .price(new BigDecimal("300.00")).unitValue(new BigDecimal("30.00"))
                .paymentMethod(CashPaymentMethod.PIX).purchasedAt(LocalDateTime.now())
                .expiresAt(expiresAt).build();
    }

    /** O banco devolveria so os usaveis; aqui o mock devolve o que for dado, para testar a defesa. */
    private void givenPackages(CustomerPackage... packages) {
        when(customerPackageRepository.findUsable(eq(shop), any())).thenReturn(List.of(packages));
        when(customerPackageRepository.lockUsableForCustomers(eq(shop), anyCollection(), any()))
                .thenReturn(List.of(packages));
    }

    private Comanda comanda(String phone) {
        return Comanda.builder().id(UUID.randomUUID()).barbershopId(shop).customerPhone(phone)
                .appointmentId(42L).status(Comanda.Status.ABERTA).comandaDate(today).build();
    }

    private ComandaItem service(UUID serviceId, String name, String price, int qty) {
        BigDecimal p = new BigDecimal(price);
        return ComandaItem.builder().id(UUID.randomUUID()).barbershopId(shop).kind(ComandaItem.Kind.SERVICO)
                .referenceId(serviceId).description(name).unitPrice(p).quantity(qty)
                .total(p.multiply(BigDecimal.valueOf(qty))).build();
    }

    private static BigDecimal sum(List<ComandaItem> items) {
        return items.stream().map(ComandaItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- catalogo ----------

    @Test
    @DisplayName("catálogo recusa quantidade zero e nome repetido")
    void create_validates() {
        assertThatThrownBy(() -> service.create("X", null, 0, new BigDecimal("10"), 30))
                .hasMessageContaining("usos");
        when(packageRepository.findByBarbershopIdAndNameIgnoreCase(shop, "10 cortes"))
                .thenReturn(Optional.of(catalog(true)));
        assertThatThrownBy(() -> service.create("10 cortes", null, 10, new BigDecimal("300"), 90))
                .hasMessageContaining("Ja existe");
    }

    // ---------- venda ----------

    @Test
    @DisplayName("venda lança no caixa e congela preço, valor unitário e validade")
    void sell_postsToCashAndFreezes() {
        ServicePackage p = catalog(true);
        when(packageRepository.findByIdAndBarbershopId(p.getId(), shop)).thenReturn(Optional.of(p));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("5511988887777")));
        UUID movementId = UUID.randomUUID();
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(movementId).build());

        CustomerPackage sold = service.sell(p.getId(), customerId, CashPaymentMethod.PIX);

        verify(cashRegisterService).recordMovement(eq(CashMovementType.VENDA), eq(CashPaymentMethod.PIX),
                eq(new BigDecimal("300.00")), any());
        assertThat(sold.getCashMovementId()).isEqualTo(movementId);
        assertThat(sold.getRemainingUses()).isEqualTo(10);
        assertThat(sold.getUnitValue()).isEqualByComparingTo("30.00");
        assertThat(sold.getExpiresAt()).isEqualTo(today.plusDays(90));
    }

    @Test
    @DisplayName("sem caixa aberto a venda é recusada e nenhum pacote é gravado")
    void sell_withoutOpenCashRegister_isRefused() {
        ServicePackage p = catalog(true);
        when(packageRepository.findByIdAndBarbershopId(p.getId(), shop)).thenReturn(Optional.of(p));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("5511988887777")));
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.sell(p.getId(), customerId, CashPaymentMethod.DINHEIRO))
                .hasMessageContaining("caixa aberto");
        verify(customerPackageRepository, never()).save(any());
    }

    @Test
    @DisplayName("pacote inativo não é vendido")
    void sell_inactivePackage_isRefused() {
        ServicePackage p = catalog(false);
        when(packageRepository.findByIdAndBarbershopId(p.getId(), shop)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.sell(p.getId(), customerId, CashPaymentMethod.PIX))
                .hasMessageContaining("inativo");
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    // ---------- baixa ----------

    @Test
    @DisplayName("serviço coberto sai a zero, consome um uso e grava a baixa auditável")
    void consume_coveredService() {
        CustomerPackage p = pkg(corteId, 3, today.plusDays(10));
        givenPackages(p);
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);
        ComandaItem barba = service(barbaId, "Barba", "30.00", 1);

        // telefone em outro formato: normalizado, casa com o cadastro
        List<ComandaItem> out = service.consumeForComanda(comanda("(11) 98888-7777"), List.of(corte, barba));

        assertThat(sum(out)).isEqualByComparingTo("30.00");
        assertThat(corte.getDescription()).endsWith("(pacote)");
        assertThat(p.getRemainingUses()).isEqualTo(2);
        ArgumentCaptor<PackageUsage> captor = ArgumentCaptor.forClass(PackageUsage.class);
        verify(usageRepository).save(captor.capture());
        PackageUsage u = captor.getValue();
        assertThat(u.getComandaItemId()).isEqualTo(corte.getId());
        assertThat(u.getAppointmentId()).isEqualTo(42L);
        assertThat(u.getUnitValue()).isEqualByComparingTo("30.00");
        assertThat(u.getOriginalPrice()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("pacote vencido não paga nada")
    void consume_expiredPackage_isIgnored() {
        givenPackages(pkg(corteId, 3, today.minusDays(1)));
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);

        List<ComandaItem> out = service.consumeForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(sum(out)).isEqualByComparingTo("40.00");
        verify(usageRepository, never()).save(any());
    }

    @Test
    @DisplayName("pacote com saldo zero não paga nada")
    void consume_zeroBalance_isIgnored() {
        givenPackages(pkg(corteId, 0, today.plusDays(10)));
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);

        List<ComandaItem> out = service.consumeForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(sum(out)).isEqualByComparingTo("40.00");
        verify(usageRepository, never()).save(any());
    }

    @Test
    @DisplayName("item que já tem baixa não desconta de novo (idempotência)")
    void consume_twice_doesNotDoubleCount() {
        CustomerPackage p = pkg(corteId, 3, today.plusDays(10));
        givenPackages(p);
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);
        when(usageRepository.existsByComandaItemId(corte.getId())).thenReturn(true);

        service.consumeForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(p.getRemainingUses()).isEqualTo(3);
        verify(usageRepository, never()).save(any());
    }

    @Test
    @DisplayName("reprocessar a mesma comanda não consome outro uso (item já zerado)")
    void consume_reprocess_doesNotConsumeAgain() {
        CustomerPackage p = pkg(corteId, 3, today.plusDays(10));
        givenPackages(p);
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);
        Comanda c = comanda("5511988887777");

        service.consumeForComanda(c, List.of(corte));
        service.consumeForComanda(c, List.of(corte));

        assertThat(p.getRemainingUses()).isEqualTo(2);
    }

    @Test
    @DisplayName("saldo menor que a quantidade: divide o item, cobra o resto")
    void consume_partialBalance_splitsItem() {
        CustomerPackage p = pkg(corteId, 1, today.plusDays(10));
        givenPackages(p);
        ComandaItem corte = service(corteId, "Corte", "40.00", 2);

        List<ComandaItem> out = service.consumeForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(out).hasSize(2);
        assertThat(sum(out)).isEqualByComparingTo("40.00");
        assertThat(corte.getQuantity()).isEqualTo(1);
        assertThat(p.getRemainingUses()).isZero();
    }

    @Test
    @DisplayName("pacote de um serviço não paga outro serviço")
    void consume_otherService_isNotCovered() {
        givenPackages(pkg(corteId, 3, today.plusDays(10)));
        ComandaItem barba = service(barbaId, "Barba", "30.00", 1);

        List<ComandaItem> out = service.consumeForComanda(comanda("5511988887777"), List.of(barba));

        assertThat(sum(out)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("comanda sem telefone não procura pacote")
    void consume_withoutPhone_isUntouched() {
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);

        List<ComandaItem> out = service.consumeForComanda(comanda(null), List.of(corte));

        assertThat(sum(out)).isEqualByComparingTo("40.00");
        verify(customerPackageRepository, never()).findUsable(any(), any());
    }

    // ---------- revisao 2026-11-18 ----------

    @Test
    @DisplayName("preview simula a baixa sem tocar no saldo nem gravar nada")
    void preview_doesNotWrite() {
        CustomerPackage p = pkg(corteId, 3, today.plusDays(10));
        when(customerPackageRepository.findUsableForCustomers(eq(shop), anyCollection(), any()))
                .thenReturn(List.of(p));
        ComandaItem corte = service(corteId, "Corte", "40.00", 2);

        List<PackageService.Coverage> cov = service.previewForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(cov).hasSize(1);
        assertThat(cov.get(0).uses()).isEqualTo(2);
        assertThat(cov.get(0).coveredValue()).isEqualByComparingTo("80.00");
        assertThat(p.getRemainingUses()).isEqualTo(3);
        assertThat(corte.getTotal()).isEqualByComparingTo("80.00");
        verify(customerPackageRepository, never()).lockUsableForCustomers(any(), any(), any());
        verify(customerPackageRepository, never()).save(any());
        verify(comandaItemRepository, never()).save(any());
        verify(usageRepository, never()).save(any());
    }

    @Test
    @DisplayName("dono do pacote é achado pelas grafias do telefone (com/sem 55, com/sem 9)")
    void owners_lookedUpByPhoneVariants() {
        givenPackages(pkg(corteId, 3, today.plusDays(10)));
        ComandaItem corte = service(corteId, "Corte", "40.00", 1);

        service.consumeForComanda(comanda("(11) 8888-7777"), List.of(corte));

        verify(customerRepository).findByBarbershopIdAndPhoneNumberIn(eq(shop),
                org.mockito.ArgumentMatchers.argThat(c -> c.contains("5511988887777") && c.contains("551188887777")));
    }

    @Test
    @DisplayName("último uso vale o resto do preço: a soma das baixas fecha o preço pago")
    void lastUse_takesRemainder() {
        // 100 / 3 = 33,33 por uso; o terceiro vale 33,34.
        CustomerPackage p = CustomerPackage.builder().id(UUID.randomUUID()).barbershopId(shop).customerId(customerId)
                .packageName("3 cortes").serviceId(corteId).totalUses(3).remainingUses(2)
                .price(new BigDecimal("100.00")).unitValue(new BigDecimal("33.33"))
                .paymentMethod(CashPaymentMethod.PIX).purchasedAt(LocalDateTime.now())
                .expiresAt(today.plusDays(10)).build();
        givenPackages(p);
        ComandaItem corte = service(corteId, "Corte", "40.00", 2);
        List<PackageUsage> usos = new java.util.ArrayList<>();
        when(usageRepository.save(any(PackageUsage.class))).thenAnswer(inv -> {
            usos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        List<ComandaItem> out = service.consumeForComanda(comanda("5511988887777"), List.of(corte));

        assertThat(sum(out)).isEqualByComparingTo("0");
        assertThat(p.getRemainingUses()).isZero();
        assertThat(usos).hasSize(2);
        assertThat(usos.get(0).getUnitValue()).isEqualByComparingTo("33.33");
        assertThat(usos.get(1).getUnitValue()).isEqualByComparingTo("33.34");
        assertThat(usos.get(0).getComandaItemId()).isNotEqualTo(usos.get(1).getComandaItemId());
    }
}

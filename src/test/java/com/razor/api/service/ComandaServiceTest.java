package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.CashMovement;
import com.razor.api.model.Coupon;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.util.AppointmentDates;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.model.Product;
import com.razor.api.model.ProductSale;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ComandaItemRepository;
import com.razor.api.repository.ComandaRepository;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
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
 * Testes da comanda (V22). O que mais importa aqui: o dinheiro entrar <b>uma vez so</b> no
 * caixa, e precos congelados nao mudarem depois.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComandaServiceTest {

    @Mock private ComandaRepository comandaRepository;
    @Mock private ComandaItemRepository itemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSaleRepository productSaleRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CashRegisterService cashRegisterService;
    @Mock private StockService stockService;
    @Mock private PackageService packageService;
    @Mock private FidelityVisitService fidelityVisitService;
    @Mock private CustomerReviewService customerReviewService;
    @Mock private PricingService pricingService;
    @Mock private CouponService couponService;
    @Mock private AppointmentRepository appointmentRepository;

    @InjectMocks private ComandaService service;

    private final UUID comandaId = UUID.randomUUID();
    private final UUID movementId = UUID.randomUUID();

    @BeforeEach
    void noPackageByDefault() {
        // Sem pacote (V34), a baixa devolve os itens como vieram.
        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Comanda openComanda(UUID professionalId) {
        Comanda c = Comanda.builder()
                .id(comandaId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .customerName("João").professionalId(professionalId)
                .status(Comanda.Status.ABERTA)
                .openedAt(LocalDateTime.now()).comandaDate(LocalDate.of(2026, 7, 27))
                .build();
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(c));
        when(comandaRepository.lockByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(c));
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(inv -> inv.getArgument(0));
        return c;
    }

    private ComandaItem item(ComandaItem.Kind kind, String desc, String unit, int qty) {
        BigDecimal u = new BigDecimal(unit);
        return ComandaItem.builder()
                .id(UUID.randomUUID()).comandaId(comandaId)
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .kind(kind).referenceId(UUID.randomUUID()).description(desc)
                .unitPrice(u).quantity(qty).total(u.multiply(BigDecimal.valueOf(qty)))
                .build();
    }

    private void givenItems(ComandaItem... items) {
        when(itemRepository.findByComandaIdOrderByCreatedAtAsc(comandaId)).thenReturn(List.of(items));
    }

    private void givenCashAccepts() {
        when(cashRegisterService.recordMovement(any(), any(), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(movementId).build());
    }

    // ---------- itens ----------

    @Test
    @DisplayName("serviço entra na comanda com nome e preço congelados do catálogo")
    void addService_freezesCatalogPrice() {
        Comanda c = openComanda(null);
        UUID svcId = UUID.randomUUID();
        when(serviceOfferingRepository.findById(svcId)).thenReturn(Optional.of(ServiceOffering.builder()
                .id(svcId).barbershopId(c.getBarbershopId()).name("Corte")
                .price(new BigDecimal("40.00")).active(true).build()));
        when(itemRepository.save(any(ComandaItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ComandaItem added = service.addService(comandaId, svcId, 2);

        assertThat(added.getKind()).isEqualTo(ComandaItem.Kind.SERVICO);
        assertThat(added.getDescription()).isEqualTo("Corte");
        assertThat(added.getUnitPrice()).isEqualByComparingTo("40.00");
        assertThat(added.getTotal()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("produto inativo não entra na comanda")
    void addProduct_inactive_isRejected() {
        Comanda c = openComanda(null);
        UUID prodId = UUID.randomUUID();
        when(productRepository.findByIdAndBarbershopId(eq(prodId), any())).thenReturn(Optional.of(
                Product.builder().id(prodId).barbershopId(c.getBarbershopId())
                        .name("Cera velha").price(new BigDecimal("20.00")).active(false).build()));

        assertThatThrownBy(() -> service.addProduct(comandaId, prodId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inativo");

        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("comanda fechada não aceita mais itens")
    void addItem_onClosedComanda_isRejected() {
        Comanda closed = Comanda.builder()
                .id(comandaId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .status(Comanda.Status.FECHADA).build();
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(closed));
        when(comandaRepository.lockByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.addProduct(comandaId, UUID.randomUUID(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja foi fechada");
    }

    // ---------- fidelidade no atendimento concluido ----------

    @Test
    @DisplayName("fechar comanda com servico registra o ponto de fidelidade do cliente")
    void close_withService_registersFidelityVisit() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511999990000");
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        givenCashAccepts();

        service.close(comandaId, CashPaymentMethod.PIX);

        verify(fidelityVisitService).registerCompletedVisit("5511999990000");
    }

    @Test
    @DisplayName("sem telefone na comanda, usa o do agendamento vinculado")
    void close_linkedAppointment_usesAppointmentPhone() {
        Comanda c = openComanda(null);
        c.setAppointmentId(42L);
        when(appointmentRepository.findById(42L)).thenReturn(Optional.of(
                com.razor.api.model.Appointment.builder().id(42L)
                        .barbershopId(c.getBarbershopId()).customerPhone("5511888880000").build()));
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        givenCashAccepts();

        service.close(comandaId, CashPaymentMethod.PIX);

        verify(fidelityVisitService).registerCompletedVisit("5511888880000");
    }

    @Test
    @DisplayName("comanda so de produto nao conta como visita")
    void close_onlyProducts_noFidelity() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511999990000");
        givenItems(item(ComandaItem.Kind.PRODUTO, "Pomada", "29.90", 1));
        givenCashAccepts();

        service.close(comandaId, CashPaymentMethod.PIX);

        verify(fidelityVisitService, never()).registerCompletedVisit(any());
    }

    @Test
    @DisplayName("fechamento que falha nao da ponto de fidelidade")
    void close_fails_noFidelity() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511999990000");
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        when(cashRegisterService.recordMovement(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("caixa fechado"));

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.PIX))
                .isInstanceOf(IllegalStateException.class);
        verify(fidelityVisitService, never()).registerCompletedVisit(any());
    }

    // ---------- fechamento (dinheiro) ----------

    @Test
    @DisplayName("fechamento soma serviços e produtos e lança UM único movimento no caixa")
    void close_postsSingleCashMovementWithTotal() {
        openComanda(null);
        givenItems(
                item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1),
                item(ComandaItem.Kind.SERVICO, "Barba", "30.00", 1),
                item(ComandaItem.Kind.PRODUTO, "Pomada", "29.90", 2));
        givenCashAccepts();

        Comanda closed = service.close(comandaId, CashPaymentMethod.DINHEIRO);

        // 40 + 30 + 59,80 = 129,80
        assertThat(closed.getTotal()).isEqualByComparingTo("129.80");
        assertThat(closed.getStatus()).isEqualTo(Comanda.Status.FECHADA);
        assertThat(closed.getCashMovementId()).isEqualTo(movementId);

        verify(cashRegisterService, times(1)).recordMovement(
                eq(CashMovementType.VENDA), eq(CashPaymentMethod.DINHEIRO),
                eq(new BigDecimal("129.80")), any(), eq(comandaId));
    }

    @Test
    @DisplayName("produtos da comanda viram venda apontando para o MESMO movimento — dinheiro não entra duas vezes")
    void close_productSalesShareTheComandaMovement() {
        UUID prof = UUID.randomUUID();
        openComanda(prof);
        givenItems(
                item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1),
                item(ComandaItem.Kind.PRODUTO, "Pomada", "29.90", 2),
                item(ComandaItem.Kind.PRODUTO, "Cerveja", "10.00", 1));
        givenCashAccepts();

        service.close(comandaId, CashPaymentMethod.PIX);

        ArgumentCaptor<ProductSale> captor = ArgumentCaptor.forClass(ProductSale.class);
        verify(productSaleRepository, times(2)).save(captor.capture()); // só os 2 produtos
        assertThat(captor.getAllValues())
                .allSatisfy(s -> {
                    assertThat(s.getCashMovementId()).isEqualTo(movementId); // o mesmo da comanda
                    assertThat(s.getComandaId()).isEqualTo(comandaId);
                    assertThat(s.getProfessionalId()).isEqualTo(prof);       // comissão vai pro barbeiro
                });
        // e o caixa recebeu apenas UM lançamento, com o total da comanda
        verify(cashRegisterService, times(1)).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fechar comanda dá baixa no estoque só dos itens de produto (V24)")
    void close_registersStockExitForProductsOnly() {
        openComanda(null);
        ComandaItem servico = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        ComandaItem produto = item(ComandaItem.Kind.PRODUTO, "Pomada", "29.90", 2);
        givenItems(servico, produto);
        givenCashAccepts();
        when(productSaleRepository.save(any(ProductSale.class))).thenAnswer(inv -> inv.getArgument(0));

        Product p = Product.builder()
                .id(produto.getReferenceId()).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .name("Pomada").price(new BigDecimal("29.90")).active(true)
                .trackStock(true).stockQuantity(10).build();
        when(productRepository.findByIdAndBarbershopId(eq(produto.getReferenceId()), any()))
                .thenReturn(Optional.of(p));

        service.close(comandaId, CashPaymentMethod.DINHEIRO);

        verify(stockService).registerSale(eq(p), eq(2), any());
        // serviço não tem estoque para baixar
        verify(stockService, times(1)).registerSale(any(), anyInt(), any());
    }

    @Test
    @DisplayName("sem caixa aberto o fechamento falha e a comanda continua aberta")
    void close_withoutOpenCashRegister_leavesComandaOpen() {
        Comanda c = openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        when(cashRegisterService.recordMovement(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caixa aberto");

        assertThat(c.getStatus()).isEqualTo(Comanda.Status.ABERTA);
        verify(productSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("pacote cobre tudo + split informado: fecha a zero, sem movimento de caixa")
    void close_fullyCoveredByPackage_withSplit_noCashMovement() {
        Comanda c = openComanda(null);
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        givenItems(corte);
        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> {
            corte.setUnitPrice(BigDecimal.ZERO);
            corte.setTotal(BigDecimal.ZERO);
            return List.of(corte);
        });

        Comanda closed = service.close(comandaId,
                List.of(new ComandaService.Payment(CashPaymentMethod.PIX, new BigDecimal("40.00"))), null);

        assertThat(closed.getTotal()).isEqualByComparingTo("0");
        assertThat(closed.getStatus()).isEqualTo(Comanda.Status.FECHADA);
        assertThat(closed.getCashMovementId()).isNull();
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("pacote (V34): serviço coberto sai a zero e o caixa recebe só o resto")
    void close_packageCoveredServiceIsNotCharged() {
        openComanda(null);
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        ComandaItem barba = item(ComandaItem.Kind.SERVICO, "Barba", "30.00", 1);
        givenItems(corte, barba);
        givenCashAccepts();
        // O pacote paga o corte.
        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> {
            corte.setUnitPrice(BigDecimal.ZERO);
            corte.setTotal(BigDecimal.ZERO);
            return List.of(corte, barba);
        });

        Comanda closed = service.close(comandaId, CashPaymentMethod.PIX);

        assertThat(closed.getTotal()).isEqualByComparingTo("30.00");
        verify(cashRegisterService).recordMovement(
                eq(CashMovementType.VENDA), eq(CashPaymentMethod.PIX), eq(new BigDecimal("30.00")), any(), any());
    }

    @Test
    @DisplayName("pacote (V34): comanda toda paga pelo pacote fecha zerada e sem movimento de caixa")
    void close_fullyCoveredByPackage_closesWithoutCashMovement() {
        openComanda(null);
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        givenItems(corte);
        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> {
            corte.setUnitPrice(BigDecimal.ZERO);
            corte.setTotal(BigDecimal.ZERO);
            return List.of(corte);
        });

        Comanda closed = service.close(comandaId, CashPaymentMethod.DINHEIRO);

        assertThat(closed.getStatus()).isEqualTo(Comanda.Status.FECHADA);
        assertThat(closed.getTotal()).isEqualByComparingTo("0");
        assertThat(closed.getCashMovementId()).isNull();
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("comanda vazia não pode ser fechada e não toca no caixa")
    void close_emptyComanda_isRejected() {
        openComanda(null);
        givenItems();

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazia");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fechar sem forma de pagamento é recusado")
    void close_withoutPaymentMethod_isRejected() {
        openComanda(null);

        assertThatThrownBy(() -> service.close(comandaId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forma de pagamento");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("comanda já fechada não fecha de novo — evita cobrar duas vezes")
    void close_twice_isRejected() {
        Comanda closed = Comanda.builder()
                .id(comandaId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .status(Comanda.Status.FECHADA).build();
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(closed));
        when(comandaRepository.lockByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    // ---------- fechamento em split (V35) ----------

    /** Caixa aceita e devolve um movimento novo (id distinto) por chamada, como o real. */
    private List<CashMovement> givenCashRecordsEachPart() {
        List<CashMovement> gravados = new java.util.ArrayList<>();
        when(cashRegisterService.recordMovement(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            CashMovement m = CashMovement.builder().id(UUID.randomUUID())
                    .type(inv.getArgument(0)).paymentMethod(inv.getArgument(1))
                    .amount(inv.getArgument(2)).comandaId(inv.getArgument(4)).build();
            gravados.add(m);
            return m;
        });
        return gravados;
    }

    private static ComandaService.Payment pay(CashPaymentMethod m, String amount) {
        return new ComandaService.Payment(m, new BigDecimal(amount));
    }

    @Test
    @DisplayName("split: cada parte vira o seu movimento de caixa, com a sua forma e amarrado à comanda")
    void closeSplit_createsOneMovementPerPart() {
        openComanda(null);
        givenItems(
                item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1),
                item(ComandaItem.Kind.PRODUTO, "Pomada", "30.00", 1));
        List<CashMovement> gravados = givenCashRecordsEachPart();

        Comanda closed = service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "20"),
                pay(CashPaymentMethod.CREDITO, "45.00"),
                pay(CashPaymentMethod.PIX, "15.00")), null);

        assertThat(gravados).hasSize(3);
        assertThat(gravados).extracting(CashMovement::getPaymentMethod).containsExactly(
                CashPaymentMethod.DINHEIRO, CashPaymentMethod.CREDITO, CashPaymentMethod.PIX);
        assertThat(gravados).extracting(CashMovement::getAmount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("20"), new BigDecimal("45"), new BigDecimal("15"));
        assertThat(gravados).allSatisfy(m -> {
            assertThat(m.getType()).isEqualTo(CashMovementType.VENDA);
            assertThat(m.getComandaId()).isEqualTo(comandaId);
        });

        // A parte principal (a maior) é a que fica na comanda e nos produtos vendidos.
        CashMovement credito = gravados.get(1);
        assertThat(closed.getTotal()).isEqualByComparingTo("80.00");
        assertThat(closed.getPaymentMethod()).isEqualTo(CashPaymentMethod.CREDITO);
        assertThat(closed.getCashMovementId()).isEqualTo(credito.getId());
        ArgumentCaptor<ProductSale> captor = ArgumentCaptor.forClass(ProductSale.class);
        verify(productSaleRepository).save(captor.capture());
        assertThat(captor.getValue().getCashMovementId()).isEqualTo(credito.getId());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(CashPaymentMethod.CREDITO);
    }

    @Test
    @DisplayName("split: soma menor que o total é recusada, diz quanto falta e não toca no caixa")
    void closeSplit_sumBelowTotal_isRejected() {
        Comanda c = openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1));

        assertThatThrownBy(() -> service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "20.00"),
                pay(CashPaymentMethod.PIX, "29.99")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faltam R$ 0,01");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
        assertThat(c.getStatus()).isEqualTo(Comanda.Status.ABERTA);
    }

    @Test
    @DisplayName("split: soma maior que o total é recusada")
    void closeSplit_sumAboveTotal_isRejected() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1));

        assertThatThrownBy(() -> service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "50.00"),
                pay(CashPaymentMethod.PIX, "10.00")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobram R$ 10,00");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("split: parte zerada ou sem forma é recusada")
    void closeSplit_invalidPart_isRejected() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1));

        assertThatThrownBy(() -> service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "50.00"),
                pay(CashPaymentMethod.PIX, "0")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> service.close(comandaId, List.of(
                new ComandaService.Payment(null, new BigDecimal("50.00"))), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forma de pagamento");
        assertThatThrownBy(() -> service.close(comandaId, List.<ComandaService.Payment>of(), null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("split com cupom: a soma tem que bater com o total JÁ descontado")
    void closeSplit_withCoupon_matchesDiscountedTotal() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1));
        Coupon cupom = Coupon.builder().id(UUID.randomUUID()).code("DEZ").build();
        when(couponService.validate(eq("DEZ"), any(), any(), any()))
                .thenReturn(new CouponService.Validation(cupom, new BigDecimal("10.00")));
        List<CashMovement> gravados = givenCashRecordsEachPart();

        // 50 - 10 = 40: pagar os 50 cheios é recusado...
        assertThatThrownBy(() -> service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "25.00"),
                pay(CashPaymentMethod.DEBITO, "25.00")), "DEZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobram R$ 10,00");
        assertThat(gravados).isEmpty();
        verify(couponService, never()).redeem(any(), any(), any(), any());

        // ...e os 40 fecham.
        Comanda closed = service.close(comandaId, List.of(
                pay(CashPaymentMethod.DINHEIRO, "25.00"),
                pay(CashPaymentMethod.DEBITO, "15.00")), "DEZ");

        assertThat(closed.getTotal()).isEqualByComparingTo("40.00");
        assertThat(gravados).hasSize(2);
        verify(couponService).redeem(eq(cupom), eq(new BigDecimal("10.00")), eq(comandaId), any());
    }

    // ---------- pre-visualizacao do fechamento (pacote + cupom) ----------

    @Test
    @DisplayName("close-preview: pacote cobre o corte e o cupom incide só sobre o resto — o fechamento cobra o mesmo total")
    void closePreview_matchesCloseTotal() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511988887777");
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        ComandaItem barba = item(ComandaItem.Kind.SERVICO, "Barba", "30.00", 1);
        givenItems(corte, barba);
        when(packageService.previewForComanda(any(), any())).thenReturn(List.of(
                new PackageService.Coverage(corte, null, 1, new BigDecimal("40.00"), new BigDecimal("30.00"))));
        Coupon cupom = Coupon.builder().id(UUID.randomUUID()).code("DEZ").build();
        when(couponService.validate(eq("DEZ"), eq(new BigDecimal("30.00")), any(), any()))
                .thenReturn(new CouponService.Validation(cupom, new BigDecimal("3.00")));

        ComandaService.ClosePreview p = service.previewClose(comandaId, "DEZ");

        assertThat(p.subtotal()).isEqualByComparingTo("70.00");
        assertThat(p.packageCovered()).isEqualByComparingTo("40.00");
        assertThat(p.couponDiscount()).isEqualByComparingTo("3.00");
        assertThat(p.total()).isEqualByComparingTo("27.00");
        assertThat(p.couponError()).isNull();
        assertThat(p.coveredItems()).singleElement().satisfies(ci -> {
            assertThat(ci.itemId()).isEqualTo(corte.getId());
            assertThat(ci.uses()).isEqualTo(1);
        });
        // Nada foi gravado nem consumido.
        verify(packageService, never()).consumeForComanda(any(), any());
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());

        // O fechamento de verdade (mesma baixa) exige exatamente o total da simulacao.
        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> {
            corte.setUnitPrice(BigDecimal.ZERO);
            corte.setTotal(BigDecimal.ZERO);
            return List.of(corte, barba);
        });
        givenCashAccepts();
        Comanda closed = service.close(comandaId, CashPaymentMethod.PIX, "DEZ");
        assertThat(closed.getTotal()).isEqualByComparingTo(p.total());
    }

    @Test
    @DisplayName("close-preview: cupom inválido vira couponError, sem derrubar a simulação")
    void closePreview_invalidCoupon_isReportedNotThrown() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        when(couponService.validate(eq("XX"), any(), any(), any()))
                .thenThrow(new NotFoundException("Cupom nao encontrado."));

        ComandaService.ClosePreview p = service.previewClose(comandaId, "XX");

        assertThat(p.couponError()).isEqualTo("Cupom nao encontrado.");
        assertThat(p.couponDiscount()).isEqualByComparingTo("0");
        assertThat(p.total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("pacote cobre tudo: o cupom não é gasto (preview avisa, fechamento não resgata)")
    void fullyCoveredByPackage_couponIsNotRedeemed() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511988887777");
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        givenItems(corte);
        when(packageService.previewForComanda(any(), any())).thenReturn(List.of(
                new PackageService.Coverage(corte, null, 1, new BigDecimal("40.00"), new BigDecimal("30.00"))));

        ComandaService.ClosePreview p = service.previewClose(comandaId, "DEZ");
        assertThat(p.total()).isEqualByComparingTo("0");
        assertThat(p.couponError()).isEqualTo(ComandaService.CUPOM_SEM_USO);

        when(packageService.consumeForComanda(any(), any())).thenAnswer(inv -> {
            corte.setUnitPrice(BigDecimal.ZERO);
            corte.setTotal(BigDecimal.ZERO);
            return List.of(corte);
        });
        Comanda closed = service.close(comandaId, CashPaymentMethod.PIX, "DEZ");

        assertThat(closed.getTotal()).isEqualByComparingTo("0");
        assertThat(closed.getCouponId()).isNull();
        verify(couponService, never()).validate(any(), any(), any(), any());
        verify(couponService, never()).redeem(any(), any(), any(), any());
    }

    @Test
    @DisplayName("fechamento trava a comanda (FOR UPDATE) antes de conferir a situação dela")
    void close_locksComandaFirst() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1));
        givenCashAccepts();

        service.close(comandaId, CashPaymentMethod.PIX);

        verify(comandaRepository).lockByIdAndBarbershopId(eq(comandaId), any());
    }

    @Test
    @DisplayName("coupon-preview antigo delega: subtotal já sem o que o pacote paga")
    void couponPreview_delegatesToClosePreview() {
        Comanda c = openComanda(null);
        c.setCustomerPhone("5511988887777");
        ComandaItem corte = item(ComandaItem.Kind.SERVICO, "Corte", "40.00", 1);
        ComandaItem barba = item(ComandaItem.Kind.SERVICO, "Barba", "30.00", 1);
        givenItems(corte, barba);
        when(packageService.previewForComanda(any(), any())).thenReturn(List.of(
                new PackageService.Coverage(corte, null, 1, new BigDecimal("40.00"), new BigDecimal("30.00"))));
        Coupon cupom = Coupon.builder().id(UUID.randomUUID()).code("DEZ").build();
        when(couponService.validate(eq("DEZ"), any(), any(), any()))
                .thenReturn(new CouponService.Validation(cupom, new BigDecimal("3.00")));

        ComandaService.CouponPreview p = service.previewCoupon(comandaId, "DEZ");

        assertThat(p.subtotal()).isEqualByComparingTo("30.00");
        assertThat(p.finalTotal()).isEqualByComparingTo("27.00");
        assertThat(p.code()).isEqualTo("DEZ");
    }

    @Test
    @DisplayName("forma única continua funcionando: uma parte com o total, amarrada à comanda")
    void closeSingleMethod_isOnePartWithTotal() {
        openComanda(null);
        givenItems(item(ComandaItem.Kind.SERVICO, "Corte", "50.00", 1));
        List<CashMovement> gravados = givenCashRecordsEachPart();

        service.close(comandaId, CashPaymentMethod.OUTRO, null);

        assertThat(gravados).singleElement().satisfies(m -> {
            assertThat(m.getPaymentMethod()).isEqualTo(CashPaymentMethod.OUTRO);
            assertThat(m.getAmount()).isEqualByComparingTo("50.00");
            assertThat(m.getComandaId()).isEqualTo(comandaId);
        });
    }

    // ---------- vinculo com agendamento (seguranca) ----------

    private Appointment appointment(long id, LocalDate date) {
        return Appointment.builder().id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .appointmentDate(date).build();
    }

    @Test
    @DisplayName("abrir comanda vinculada a agendamento de outra barbearia é recusado")
    void open_withAppointmentOfOtherTenant_isRejected() {
        when(appointmentRepository.findByIdAndBarbershopId(eq(99L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open("João", null, null, 99L))
                .isInstanceOf(NotFoundException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir comanda vinculada a agendamento de outro dia é recusado")
    void open_withAppointmentOfAnotherDay_isRejected() {
        when(appointmentRepository.findByIdAndBarbershopId(eq(7L), any()))
                .thenReturn(Optional.of(appointment(7L, AppointmentDates.today().minusDays(3))));

        assertThatThrownBy(() -> service.open("João", null, null, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoje");
        verify(comandaRepository, never()).save(any());
    }

    @Test
    @DisplayName("agendamento que já tem comanda não ganha outra")
    void open_withAppointmentAlreadyLinked_isRejected() {
        when(appointmentRepository.findByIdAndBarbershopId(eq(7L), any()))
                .thenReturn(Optional.of(appointment(7L, AppointmentDates.today())));
        when(comandaRepository.existsByAppointmentId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.open("João", null, null, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja tem uma comanda");
        verify(comandaRepository, never()).save(any());
    }

    @Test
    @DisplayName("agendamento de hoje, deste tenant e sem comanda é vinculado")
    void open_withValidAppointment_links() {
        when(appointmentRepository.findByIdAndBarbershopId(eq(7L), any()))
                .thenReturn(Optional.of(appointment(7L, AppointmentDates.today())));
        when(comandaRepository.existsByAppointmentId(7L)).thenReturn(false);
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Comanda c = service.open("João", null, null, 7L);

        assertThat(c.getAppointmentId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("comanda aberta do agendamento já vem com os serviços dele, pelo cálculo único de preço")
    void open_fromAppointment_prefillsServices() {
        com.razor.api.model.Appointment ag = appointment(7L, AppointmentDates.today());
        ag.setService("Corte de Cabelo, Barba");
        when(appointmentRepository.findByIdAndBarbershopId(eq(7L), any())).thenReturn(Optional.of(ag));
        when(comandaRepository.existsByAppointmentId(7L)).thenReturn(false);
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID corte = UUID.randomUUID();
        UUID barba = UUID.randomUUID();
        when(pricingService.quote("Corte de Cabelo, Barba")).thenReturn(new PricingService.Quote(List.of(
                new PricingService.QuoteLine(corte, "Corte de Cabelo", new BigDecimal("40.00"), 30),
                new PricingService.QuoteLine(barba, "Barba", new BigDecimal("30.00"), 30)),
                new BigDecimal("70.00"), 60, true));

        service.open("João", null, null, 7L);

        ArgumentCaptor<ComandaItem> cap = ArgumentCaptor.forClass(ComandaItem.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(ComandaItem::getReferenceId).containsExactly(corte, barba);
        assertThat(cap.getAllValues()).extracting(ComandaItem::getUnitPrice)
                .containsExactly(new BigDecimal("40.00"), new BigDecimal("30.00"));
    }

    // ---------- cancelamento e isolamento ----------

    @Test
    @DisplayName("cancelar não mexe em dinheiro")
    void cancel_doesNotTouchMoney() {
        openComanda(null);

        Comanda canceled = service.cancel(comandaId);

        assertThat(canceled.getStatus()).isEqualTo(Comanda.Status.CANCELADA);
        assertThat(canceled.getTotal()).isNull();
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any(), any());
        verify(productSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("comanda de outra barbearia não é encontrada")
    void get_otherTenant_notFound() {
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.empty());
        when(comandaRepository.lockByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(comandaId)).isInstanceOf(NotFoundException.class);
    }
}

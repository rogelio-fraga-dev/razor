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

import com.razor.api.model.CashMovement;
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
    @Mock private com.razor.api.repository.AppointmentRepository appointmentRepository;
    @Mock private FidelityVisitService fidelityVisitService;

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
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
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
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
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
                eq(new BigDecimal("129.80")), any());
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
        verify(cashRegisterService, times(1)).recordMovement(any(), any(), any(), any());
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
        when(cashRegisterService.recordMovement(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caixa aberto");

        assertThat(c.getStatus()).isEqualTo(Comanda.Status.ABERTA);
        verify(productSaleRepository, never()).save(any());
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
                eq(CashMovementType.VENDA), eq(CashPaymentMethod.PIX), eq(new BigDecimal("30.00")), any());
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

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("fechar sem forma de pagamento é recusado")
    void close_withoutPaymentMethod_isRejected() {
        openComanda(null);

        assertThatThrownBy(() -> service.close(comandaId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forma de pagamento");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("comanda já fechada não fecha de novo — evita cobrar duas vezes")
    void close_twice_isRejected() {
        Comanda closed = Comanda.builder()
                .id(comandaId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .status(Comanda.Status.FECHADA).build();
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.close(comandaId, CashPaymentMethod.DINHEIRO))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    // ---------- cancelamento e isolamento ----------

    @Test
    @DisplayName("cancelar não mexe em dinheiro")
    void cancel_doesNotTouchMoney() {
        openComanda(null);

        Comanda canceled = service.cancel(comandaId);

        assertThat(canceled.getStatus()).isEqualTo(Comanda.Status.CANCELADA);
        assertThat(canceled.getTotal()).isNull();
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
        verify(productSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("comanda de outra barbearia não é encontrada")
    void get_otherTenant_notFound() {
        when(comandaRepository.findByIdAndBarbershopId(eq(comandaId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(comandaId)).isInstanceOf(NotFoundException.class);
    }
}

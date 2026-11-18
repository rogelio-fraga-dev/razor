package com.razor.api.service;

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
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comanda por atendimento (V22): servicos + produtos na mesma conta, fechada de uma vez.
 *
 * <p><b>Dinheiro entra uma vez so.</b> O fechamento cria <b>um unico</b> movimento de caixa com o
 * total da comanda. Os itens de produto viram linhas em {@code product_sales} — para historico e
 * comissao continuarem com uma fonte de verdade so — mas apontando para <b>esse mesmo</b>
 * movimento, nunca criando movimento proprio. Por isso o fechamento nao passa pelo
 * {@link ProductSaleService}, que existe para a venda avulsa de balcao.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final ComandaItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final ProductSaleRepository productSaleRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ProfessionalRepository professionalRepository;
    private final CashRegisterService cashRegisterService;
    private final StockService stockService;
    private final CouponService couponService;
    private final PackageService packageService;
    private final com.razor.api.repository.AppointmentRepository appointmentRepository;
    private final FidelityVisitService fidelityVisitService;

    // ---------- consultas ----------

    /** Comandas abertas do tenant, da mais antiga para a mais nova (quem chegou primeiro). */
    public List<Comanda> listOpen() {
        return comandaRepository.findByBarbershopIdAndStatusOrderByOpenedAtAsc(
                TenantContext.getCurrent(), Comanda.Status.ABERTA);
    }

    /** Comandas de um dia (todas as situacoes). */
    public List<Comanda> listByDate(LocalDate date) {
        return comandaRepository.findByBarbershopIdAndComandaDateOrderByOpenedAtDesc(
                TenantContext.getCurrent(), date);
    }

    public Comanda get(UUID id) {
        return comandaRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Comanda nao encontrada."));
    }

    public List<ComandaItem> items(UUID comandaId) {
        get(comandaId); // valida tenant antes de listar
        return itemRepository.findByComandaIdOrderByCreatedAtAsc(comandaId);
    }

    /** Soma dos itens — o que a comanda vale agora. */
    public BigDecimal currentTotal(UUID comandaId) {
        return itemRepository.findByComandaIdOrderByCreatedAtAsc(comandaId).stream()
                .map(ComandaItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- ciclo de vida ----------

    @Transactional
    public Comanda open(String customerName, String customerPhone, UUID professionalId) {
        return open(customerName, customerPhone, professionalId, null);
    }

    /**
     * Abre a comanda, opcionalmente vinculada ao agendamento que a originou (V28). Com vinculo,
     * o financeiro sabe que o servico ja esta representado pelo agendamento e nao soma de novo.
     */
    @Transactional
    public Comanda open(String customerName, String customerPhone, UUID professionalId,
                        Long appointmentId) {
        UUID shop = TenantContext.getCurrent();
        requireProfessionalOfThisShop(professionalId, shop);

        Comanda comanda = comandaRepository.save(Comanda.builder()
                .barbershopId(shop)
                .customerName(blankToNull(customerName))
                .customerPhone(blankToNull(customerPhone))
                .professionalId(professionalId)
                .appointmentId(appointmentId)
                .status(Comanda.Status.ABERTA)
                .openedAt(LocalDateTime.now())
                .comandaDate(AppointmentDates.today())
                .build());
        log.info("[COMANDA] Aberta {} no tenant {}", comanda.getId(), shop);
        return comanda;
    }

    /** Adiciona um servico do catalogo, congelando nome e preco. */
    @Transactional
    public ComandaItem addService(UUID comandaId, UUID serviceId, int quantity) {
        Comanda comanda = requireOpen(comandaId);
        requirePositiveQuantity(quantity);

        ServiceOffering svc = serviceOfferingRepository.findById(serviceId)
                .filter(s -> comanda.getBarbershopId().equals(s.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Servico nao encontrado."));
        if (!svc.isActive()) {
            throw new IllegalArgumentException("O servico \"" + svc.getName() + "\" esta inativo.");
        }
        return saveItem(comanda, ComandaItem.Kind.SERVICO, svc.getId(), svc.getName(), svc.getPrice(), quantity);
    }

    /** Adiciona um produto do catalogo, congelando nome e preco. */
    @Transactional
    public ComandaItem addProduct(UUID comandaId, UUID productId, int quantity) {
        Comanda comanda = requireOpen(comandaId);
        requirePositiveQuantity(quantity);

        Product product = productRepository.findByIdAndBarbershopId(productId, comanda.getBarbershopId())
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado."));
        if (!product.isActive()) {
            throw new IllegalArgumentException("O produto \"" + product.getName() + "\" esta inativo.");
        }
        // Aviso cedo: melhor barrar aqui do que deixar o item entrar e o fechamento falhar
        // depois, com o cliente ja esperando para pagar.
        stockService.requireAvailable(product, quantity);
        return saveItem(comanda, ComandaItem.Kind.PRODUTO, product.getId(), product.getName(),
                product.getPrice(), quantity);
    }

    @Transactional
    public void removeItem(UUID comandaId, UUID itemId) {
        requireOpen(comandaId);
        ComandaItem item = itemRepository.findByIdAndComandaId(itemId, comandaId)
                .orElseThrow(() -> new NotFoundException("Item nao encontrado nesta comanda."));
        itemRepository.delete(item);
    }

    /**
     * Fecha a comanda: congela o total, lanca <b>um</b> movimento de caixa e registra os produtos
     * como venda (apontando para esse mesmo movimento, para o dinheiro nao entrar duas vezes).
     */
    @Transactional
    public Comanda close(UUID comandaId, CashPaymentMethod method) {
        return close(comandaId, method, null);
    }

    /**
     * Fecha a comanda aplicando um cupom, se houver (V29). O cupom e validado <b>antes</b> de
     * lancar no caixa: cupom invalido derruba o fechamento inteiro em vez de cobrar o valor
     * errado.
     */
    @Transactional
    public Comanda close(UUID comandaId, CashPaymentMethod method, String couponCode) {
        Comanda comanda = requireOpen(comandaId);
        if (method == null) {
            throw new IllegalArgumentException("Escolha a forma de pagamento da comanda.");
        }

        List<ComandaItem> items = itemRepository.findByComandaIdOrderByCreatedAtAsc(comandaId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Comanda vazia: adicione ao menos um item antes de fechar.");
        }
        // Pacote pre-pago (V34): servico coberto sai a R$ 0,00 e consome um uso. Antes do total,
        // e na mesma transacao — se o caixa recusar abaixo, a baixa volta junto.
        items = packageService.consumeForComanda(comanda, items);
        BigDecimal subtotal = items.stream().map(ComandaItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal desconto = BigDecimal.ZERO;
        CouponService.Validation cupom = null;
        if (couponCode != null && !couponCode.isBlank()) {
            cupom = couponService.validate(couponCode, subtotal, comanda.getCustomerPhone(),
                    AppointmentDates.today());
            desconto = cupom.discount();
        }
        BigDecimal total = subtotal.subtract(desconto);

        // Um unico movimento com o total. Se nao houver caixa aberto, o fechamento inteiro falha.
        String description = "Comanda" + (comanda.getCustomerName() == null
                ? "" : " — " + comanda.getCustomerName());
        // Comanda toda paga por pacote fecha com total zero e sem movimento: nao entrou dinheiro.
        CashMovement movement = total.signum() > 0
                ? cashRegisterService.recordMovement(CashMovementType.VENDA, method, total, description)
                : null;
        UUID movementId = movement != null ? movement.getId() : null;

        LocalDateTime now = LocalDateTime.now();
        registerProductSales(comanda, items, method, movementId, now);

        comanda.setStatus(Comanda.Status.FECHADA);
        comanda.setTotal(total);
        comanda.setPaymentMethod(method);
        comanda.setCashMovementId(movementId);
        comanda.setClosedAt(now);
        if (cupom != null) {
            comanda.setCouponId(cupom.coupon().getId());
            comanda.setDiscountAmount(desconto);
        }
        Comanda saved = comandaRepository.save(comanda);

        // O uso e registrado depois do caixa aceitar: cupom nao pode ficar "gasto" numa
        // comanda que falhou em cobrar.
        if (cupom != null) {
            couponService.redeem(cupom.coupon(), desconto, comanda.getId(), comanda.getCustomerPhone());
        }

        log.info("[COMANDA] Fechada {} por {} ({}) no tenant {}",
                comanda.getId(), total, method, comanda.getBarbershopId());

        registrarFidelidadeAposCommit(comanda, items);
        return saved;
    }

    /** Cancela uma comanda aberta. Nao mexe em dinheiro — comanda cancelada nunca foi cobrada. */
    @Transactional
    public Comanda cancel(UUID comandaId) {
        Comanda comanda = requireOpen(comandaId);
        comanda.setStatus(Comanda.Status.CANCELADA);
        comanda.setClosedAt(LocalDateTime.now());
        log.info("[COMANDA] Cancelada {} no tenant {}", comandaId, comanda.getBarbershopId());
        return comandaRepository.save(comanda);
    }

    // ---------- apoio ----------

    /**
     * O ponto de fidelidade e dado aqui, quando o SERVICO foi prestado e pago — e nao mais no
     * agendamento. So conta comanda com ao menos um servico (venda so de produto nao e visita).
     *
     * <p>Roda depois do commit: uma falha na fidelidade (ou o envio do cupom) nunca pode desfazer
     * o fechamento do caixa, e o ponto nao pode ser dado se o fechamento sofrer rollback.
     */
    private void registrarFidelidadeAposCommit(Comanda comanda, List<ComandaItem> items) {
        boolean temServico = items.stream().anyMatch(i -> i.getKind() == ComandaItem.Kind.SERVICO);
        if (!temServico) return;
        String phone = telefoneDoCliente(comanda);
        if (phone == null) return;

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            fidelityVisitService.registerCompletedVisit(phone);
                        }
                    });
        } else {
            fidelityVisitService.registerCompletedVisit(phone);
        }
    }

    /** Telefone da comanda; sem ele, o do agendamento vinculado (mesmo tenant). */
    private String telefoneDoCliente(Comanda comanda) {
        if (comanda.getCustomerPhone() != null && !comanda.getCustomerPhone().isBlank()) {
            return comanda.getCustomerPhone();
        }
        if (comanda.getAppointmentId() == null) return null;
        return appointmentRepository.findById(comanda.getAppointmentId())
                .filter(a -> comanda.getBarbershopId().equals(a.getBarbershopId()))
                .map(com.razor.api.model.Appointment::getCustomerPhone)
                .filter(p -> !p.isBlank() && !"N/A".equals(p))
                .orElse(null);
    }

    /**
     * Produtos da comanda viram venda para historico e comissao. O {@code cashMovementId} e o da
     * comanda, de proposito: a linha registra a venda sem lancar dinheiro de novo no caixa.
     */
    private void registerProductSales(Comanda comanda, List<ComandaItem> items,
                                      CashPaymentMethod method, UUID movementId, LocalDateTime now) {
        for (ComandaItem item : items) {
            if (item.getKind() != ComandaItem.Kind.PRODUTO) continue;
            ProductSale sale = productSaleRepository.save(ProductSale.builder()
                    .barbershopId(comanda.getBarbershopId())
                    .productId(item.getReferenceId())
                    .productName(item.getDescription())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .total(item.getTotal())
                    .professionalId(comanda.getProfessionalId())
                    .paymentMethod(method)
                    .cashMovementId(movementId)
                    .comandaId(comanda.getId())
                    .saleDate(comanda.getComandaDate())
                    .soldAt(now)
                    .build());

            // Baixa no estoque pelo mesmo caminho da venda de balcao. O produto pode ter saido
            // do catalogo entre a entrada do item e o fechamento — nesse caso nao ha o que baixar.
            productRepository.findByIdAndBarbershopId(item.getReferenceId(), comanda.getBarbershopId())
                    .ifPresentOrElse(
                            product -> stockService.registerSale(product, item.getQuantity(), sale.getId()),
                            // Sem aviso, o saldo divergiria em silencio e o dono nao teria como
                            // investigar depois por que o estoque nao bate.
                            () -> log.warn("[ESTOQUE] Produto {} ('{}') sumiu do catalogo entre a entrada na "
                                            + "comanda {} e o fechamento — a baixa NAO foi feita",
                                    item.getReferenceId(), item.getDescription(), comanda.getId()));
        }
    }

    private ComandaItem saveItem(Comanda comanda, ComandaItem.Kind kind, UUID referenceId,
                                 String description, BigDecimal unitPrice, int quantity) {
        return itemRepository.save(ComandaItem.builder()
                .comandaId(comanda.getId())
                .barbershopId(comanda.getBarbershopId())
                .kind(kind)
                .referenceId(referenceId)
                .description(description)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .total(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build());
    }

    /** Comanda fechada ou cancelada nao aceita mais mudanca — a conta ja virou dinheiro. */
    private Comanda requireOpen(UUID comandaId) {
        Comanda comanda = get(comandaId);
        if (comanda.getStatus() != Comanda.Status.ABERTA) {
            throw new IllegalArgumentException(
                    "Esta comanda ja foi " + comanda.getStatus().name().toLowerCase() + ".");
        }
        return comanda;
    }

    private void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
    }

    private void requireProfessionalOfThisShop(UUID professionalId, UUID shop) {
        if (professionalId == null) return;
        professionalRepository.findById(professionalId)
                .filter(p -> shop.equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

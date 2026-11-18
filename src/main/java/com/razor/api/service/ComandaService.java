package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.model.Product;
import com.razor.api.model.ProductSale;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ComandaItemRepository;
import com.razor.api.repository.ComandaRepository;
import com.razor.api.repository.ProductRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comanda por atendimento (V22): servicos + produtos na mesma conta, fechada de uma vez.
 *
 * <p><b>Dinheiro entra uma vez so.</b> O fechamento cria um movimento de caixa por parte do
 * pagamento (V35 — um so quando a forma e unica), e a soma das partes e exatamente o total da
 * comanda. Os itens de produto viram linhas em {@code product_sales} — para historico e
 * comissao continuarem com uma fonte de verdade so — mas apontando para o movimento
 * <b>principal</b> da comanda, nunca criando movimento proprio. Por isso o fechamento nao passa pelo
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
    private final AppointmentRepository appointmentRepository;

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
        requireLinkableAppointment(appointmentId, shop);

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

    /** Uma parte do pagamento da comanda (V35): forma + valor. */
    public record Payment(CashPaymentMethod method, BigDecimal amount) {}

    /**
     * Fecha a comanda com forma de pagamento unica (contrato anterior ao split): o total inteiro
     * entra nessa forma.
     */
    @Transactional
    public Comanda close(UUID comandaId, CashPaymentMethod method) {
        return close(comandaId, method, null);
    }

    /** Forma unica + cupom opcional (V29). Ver {@link #close(UUID, List, String)}. */
    @Transactional
    public Comanda close(UUID comandaId, CashPaymentMethod method, String couponCode) {
        requireOpen(comandaId);
        if (method == null) {
            throw new IllegalArgumentException("Escolha a forma de pagamento da comanda.");
        }
        return doClose(comandaId, null, method, couponCode);
    }

    /**
     * Fecha a comanda com uma ou mais partes de pagamento (split, V35) e cupom opcional (V29).
     *
     * <p>O cupom e validado <b>antes</b> de lancar no caixa: cupom invalido derruba o fechamento
     * inteiro em vez de cobrar o valor errado. A soma das partes tem que bater <b>exatamente</b>
     * com o total ja descontado — sobra ou falta de centavo e recusada, porque cada parte vira
     * dinheiro de verdade no caixa e na conferencia da gaveta.
     *
     * <p>Cada parte vira o <b>seu</b> movimento de caixa, com a sua forma: so a parte em dinheiro
     * entra na gaveta esperada. Os produtos da comanda continuam apontando para <b>um</b>
     * movimento (a parte principal, a de maior valor), para o dinheiro nao entrar duas vezes.
     */
    @Transactional
    public Comanda close(UUID comandaId, List<Payment> payments, String couponCode) {
        requireOpen(comandaId);
        if (payments == null || payments.isEmpty()) {
            throw new IllegalArgumentException("Informe como a comanda foi paga.");
        }
        return doClose(comandaId, payments, null, couponCode);
    }

    /**
     * Fechamento de fato. Recebe ou as partes ({@code payments}) ou a forma unica
     * ({@code singleMethod}), que vira uma parte so com o total.
     */
    private Comanda doClose(UUID comandaId, List<Payment> payments, CashPaymentMethod singleMethod,
                            String couponCode) {
        Comanda comanda = requireOpen(comandaId);

        List<ComandaItem> items = itemRepository.findByComandaIdOrderByCreatedAtAsc(comandaId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Comanda vazia: adicione ao menos um item antes de fechar.");
        }
        BigDecimal subtotal = items.stream().map(ComandaItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal desconto = BigDecimal.ZERO;
        CouponService.Validation cupom = null;
        if (couponCode != null && !couponCode.isBlank()) {
            cupom = couponService.validate(couponCode, subtotal, comanda.getCustomerPhone(),
                    AppointmentDates.today());
            desconto = cupom.discount();
        }
        BigDecimal total = subtotal.subtract(desconto);

        List<Payment> parts = payments != null ? validateParts(payments, total)
                : List.of(new Payment(singleMethod, total));

        // Um movimento por parte. Se nao houver caixa aberto, o fechamento inteiro falha (e a
        // transacao desfaz as partes que ja tivessem entrado).
        String description = "Comanda" + (comanda.getCustomerName() == null
                ? "" : " — " + comanda.getCustomerName());
        if (parts.size() > 1) description += " (split)";
        CashMovement principal = null;
        Payment principalPart = null;
        for (Payment part : parts) {
            CashMovement movement = cashRegisterService.recordMovement(
                    CashMovementType.VENDA, part.method(), part.amount(), description, comanda.getId());
            // Principal = a maior parte; empate fica com a primeira informada.
            if (principalPart == null || part.amount().compareTo(principalPart.amount()) > 0) {
                principal = movement;
                principalPart = part;
            }
        }
        CashPaymentMethod method = principalPart.method();

        LocalDateTime now = LocalDateTime.now();
        registerProductSales(comanda, items, method, principal.getId(), now);

        comanda.setStatus(Comanda.Status.FECHADA);
        comanda.setTotal(total);
        comanda.setPaymentMethod(method);
        comanda.setCashMovementId(principal.getId());
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
                comanda.getId(), total, parts.size() > 1 ? parts : method, comanda.getBarbershopId());
        return saved;
    }

    /**
     * Partes validas e normalizadas ao centavo, com a soma batendo com o total. A mensagem diz
     * quanto falta ou sobra — e o que a recepcao precisa para corrigir sem fazer conta.
     */
    private List<Payment> validateParts(List<Payment> payments, BigDecimal total) {
        BigDecimal soma = BigDecimal.ZERO;
        List<Payment> normalized = new ArrayList<>(payments.size());
        for (Payment p : payments) {
            if (p == null || p.method() == null) {
                throw new IllegalArgumentException("Escolha a forma de pagamento de cada parte.");
            }
            if (p.amount() == null || p.amount().signum() <= 0) {
                throw new IllegalArgumentException("Cada parte do pagamento deve ser maior que zero.");
            }
            BigDecimal amount = p.amount().setScale(2, RoundingMode.HALF_UP);
            soma = soma.add(amount);
            normalized.add(new Payment(p.method(), amount));
        }
        BigDecimal esperado = total.setScale(2, RoundingMode.HALF_UP);
        int cmp = soma.compareTo(esperado);
        if (cmp != 0) {
            BigDecimal diff = soma.subtract(esperado).abs();
            throw new IllegalArgumentException(String.format(
                    "A soma dos pagamentos (R$ %s) nao bate com o total da comanda (R$ %s): %s R$ %s.",
                    brl(soma), brl(esperado), cmp < 0 ? "faltam" : "sobram", brl(diff)));
        }
        return normalized;
    }

    /**
     * Simula o cupom nesta comanda (V35): o painel precisa do total ja descontado para montar o
     * split antes de fechar. O subtotal vem dos itens, nunca do cliente.
     */
    public CouponPreview previewCoupon(UUID comandaId, String couponCode) {
        Comanda comanda = requireOpen(comandaId);
        BigDecimal subtotal = currentTotal(comandaId);
        CouponService.Validation v = couponService.validate(couponCode, subtotal,
                comanda.getCustomerPhone(), AppointmentDates.today());
        return new CouponPreview(v.coupon().getCode(), subtotal, v.discount(), subtotal.subtract(v.discount()));
    }

    /** Resultado da simulacao de cupom no fechamento. */
    public record CouponPreview(String code, BigDecimal subtotal, BigDecimal discount, BigDecimal finalTotal) {}

    /** Partes do pagamento de uma comanda (V35). Vazio enquanto aberta ou cancelada. */
    public List<CashMovement> payments(Comanda comanda) {
        if (comanda.getStatus() != Comanda.Status.FECHADA) return List.of();
        return cashRegisterService.movementsOfComanda(comanda.getId());
    }

    private static String brl(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
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

    /**
     * O vinculo com agendamento tira os servicos da comanda do faturamento (o relatorio conta o
     * agendamento no lugar). Aceitar qualquer id deixaria a recepcao "sumir" com receita
     * vinculando a comanda a um agendamento de outra barbearia, antigo ou ja usado. Por isso o
     * agendamento precisa: existir NESTE tenant, ser de hoje (dia da comanda) e nao ter outra
     * comanda — a unicidade tambem esta no banco (V28), mas aqui sai com mensagem legivel.
     */
    private void requireLinkableAppointment(Long appointmentId, UUID shop) {
        if (appointmentId == null) return;
        Appointment appointment = appointmentRepository.findByIdAndBarbershopId(appointmentId, shop)
                .orElseThrow(() -> new NotFoundException("Agendamento nao encontrado."));
        if (!AppointmentDates.today().equals(appointment.getAppointmentDate())) {
            throw new IllegalArgumentException(
                    "So da para abrir comanda de um agendamento de hoje.");
        }
        if (comandaRepository.existsByAppointmentId(appointmentId)) {
            throw new IllegalArgumentException("Este agendamento ja tem uma comanda.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

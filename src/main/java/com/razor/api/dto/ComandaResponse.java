package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Comanda como o painel consome. {@code total} e o valor congelado no fechamento; enquanto
 * aberta, vale {@code currentTotal} — a soma do que esta na conta agora.
 */
public record ComandaResponse(
        UUID id,
        String customerName,
        String customerPhone,
        UUID professionalId,
        String professionalName,
        Comanda.Status status,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal currentTotal,
        BigDecimal total,
        CashPaymentMethod paymentMethod,
        List<Item> items) {

    public record Item(
            UUID id,
            ComandaItem.Kind kind,
            UUID referenceId,
            String description,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal total) {

        public static Item from(ComandaItem i) {
            return new Item(i.getId(), i.getKind(), i.getReferenceId(), i.getDescription(),
                    i.getUnitPrice(), i.getQuantity(), i.getTotal());
        }
    }

    public static ComandaResponse from(Comanda c, List<ComandaItem> items, String professionalName) {
        BigDecimal current = items.stream()
                .map(ComandaItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ComandaResponse(
                c.getId(), c.getCustomerName(), c.getCustomerPhone(),
                c.getProfessionalId(), professionalName, c.getStatus(),
                c.getOpenedAt(), c.getClosedAt(), current, c.getTotal(), c.getPaymentMethod(),
                items.stream().map(Item::from).toList());
    }
}

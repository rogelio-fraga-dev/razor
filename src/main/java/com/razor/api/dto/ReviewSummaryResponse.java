package com.razor.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Card "Avaliações dos clientes" (V46): media e distribuicao das notas do periodo, quantos pedidos
 * sairam, a taxa de resposta e as notas mais recentes.
 *
 * @param average      media do periodo (1 casa); null sem nenhuma nota
 * @param distribution quantidade por nota, de 5 a 1
 * @param responseRate notas / pedidos enviados (0..1); null sem pedido enviado
 */
public record ReviewSummaryResponse(
        int days,
        Double average,
        long count,
        Map<Integer, Long> distribution,
        long requestsSent,
        Double responseRate,
        List<Item> recent,
        boolean enabled,
        boolean googleLinkConfigured) {

    public record Item(UUID id, String customerName, int rating, String comment, LocalDateTime ratedAt,
                       String professionalName) {
    }
}

package com.razor.api.dto;

import com.razor.api.model.StockMovement;
import com.razor.api.model.StockMovementType;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha do extrato de estoque. */
public record StockMovementResponse(
        UUID id,
        StockMovementType type,
        int quantity,
        int resultingStock,
        String note,
        LocalDateTime createdAt) {

    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getType(), m.getQuantity(),
                m.getResultingStock(), m.getNote(), m.getCreatedAt());
    }
}

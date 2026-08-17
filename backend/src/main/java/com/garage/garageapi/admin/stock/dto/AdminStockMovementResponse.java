package com.garage.garageapi.admin.stock.dto;

import com.garage.garageapi.stock.entity.StockMovement;
import com.garage.garageapi.stock.entity.StockMovementType;
import com.garage.garageapi.stock.entity.StockReferenceType;

import java.time.Instant;

public record AdminStockMovementResponse(
        Long id,
        Long productId,
        String productName,
        String sku,
        StockMovementType type,
        int quantity,
        int previousStock,
        int newStock,
        String reason,
        StockReferenceType referenceType,
        Long referenceId,
        AdminStockPerformedByResponse performedBy,
        Instant createdAt
) {
    public static AdminStockMovementResponse from(StockMovement movement) {
        return new AdminStockMovementResponse(movement.getId(), movement.getProduct().getId(),
                movement.getProduct().getName(), movement.getProduct().getSku(), movement.getType(),
                movement.getQuantity(), movement.getPreviousStock(), movement.getNewStock(),
                movement.getReason(), movement.getReferenceType(), movement.getReferenceId(),
                AdminStockPerformedByResponse.from(movement.getPerformedByUser()), movement.getCreatedAt());
    }
}

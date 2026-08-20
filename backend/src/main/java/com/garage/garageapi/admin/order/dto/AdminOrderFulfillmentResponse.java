package com.garage.garageapi.admin.order.dto;

import com.garage.garageapi.order.fulfillment.OrderFulfillment;
import com.garage.garageapi.order.fulfillment.FulfillmentStatus;

import java.time.Instant;

public record AdminOrderFulfillmentResponse(
        Long orderId,
        FulfillmentStatus status,
        String provider,
        String supplierOrderId,
        String supplierShipmentOrderId,
        int attemptCount,
        String lastError,
        Instant processingStartedAt,
        Instant createdExternallyAt,
        Instant updatedAt
) {
    public static AdminOrderFulfillmentResponse from(OrderFulfillment fulfillment) {
        return new AdminOrderFulfillmentResponse(fulfillment.getOrder().getId(), fulfillment.getStatus(),
                fulfillment.getProvider(), fulfillment.getSupplierOrderId(),
                fulfillment.getSupplierShipmentOrderId(), fulfillment.getAttemptCount(),
                fulfillment.getLastError(), fulfillment.getProcessingStartedAt(),
                fulfillment.getCreatedExternallyAt(), fulfillment.getUpdatedAt());
    }
}

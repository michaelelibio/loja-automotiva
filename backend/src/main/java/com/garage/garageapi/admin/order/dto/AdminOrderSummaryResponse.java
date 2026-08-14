package com.garage.garageapi.admin.order.dto;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryResponse(
        Long id, OrderStatus status, BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total,
        Instant createdAt, Instant updatedAt, Instant processingAt, Instant shippedAt,
        Instant deliveredAt
) {
    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getId(), order.getStatus(), order.getSubtotal(), order.getShippingCost(),
                order.getTotal(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getProcessingAt(), order.getShippedAt(), order.getDeliveredAt());
    }
}

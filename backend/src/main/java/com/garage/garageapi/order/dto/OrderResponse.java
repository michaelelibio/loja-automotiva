package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, BigDecimal subtotal,
                            BigDecimal shippingCost, BigDecimal total, Instant createdAt,
                            Instant expiresAt, Instant updatedAt, ShippingAddressResponse shippingAddress,
                            OrderShippingResponse shipping, List<OrderItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getSubtotal(),
                order.getShippingCost(), order.getTotal(), order.getCreatedAt(), order.getExpiresAt(),
                order.getUpdatedAt(),
                ShippingAddressResponse.from(order), OrderShippingResponse.from(order),
                order.getItems().stream()
                .map(OrderItemResponse::from).toList());
    }
}

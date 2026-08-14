package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String productName, String productSlug,
                                BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getProductSlug(),
                item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
    }
}

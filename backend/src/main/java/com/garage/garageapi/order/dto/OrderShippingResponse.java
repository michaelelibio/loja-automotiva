package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.Order;

import java.math.BigDecimal;

public record OrderShippingResponse(
        String code, String name, BigDecimal price, Integer estimatedDays
) {
    public static OrderShippingResponse from(Order order) {
        return new OrderShippingResponse(order.getShippingCode(), order.getShippingName(),
                order.getShippingCost(), order.getShippingEstimatedDays());
    }
}

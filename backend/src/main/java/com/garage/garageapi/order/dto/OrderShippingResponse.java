package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.Order;

import java.math.BigDecimal;
import java.util.List;
import com.garage.garageapi.shipping.provider.ShippingProvider;

public record OrderShippingResponse(
        String code, String name, BigDecimal price, Integer estimatedDays,
        String provider, BigDecimal providerAmount, String providerCurrency,
        List<ShippingProvider.Leg> legs
) {
    public static OrderShippingResponse from(Order order) {
        return new OrderShippingResponse(order.getShippingCode(), order.getShippingName(),
                order.getShippingCost(), order.getShippingEstimatedDays(),
                order.getShippingProvider(), order.getShippingProviderAmount(),
                order.getShippingProviderCurrency(), order.getShippingLegs());
    }
}

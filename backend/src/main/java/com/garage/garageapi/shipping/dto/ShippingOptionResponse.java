package com.garage.garageapi.shipping.dto;

import com.garage.garageapi.shipping.provider.ShippingProvider;

import java.math.BigDecimal;

public record ShippingOptionResponse(
        String code, String name, BigDecimal price, int estimatedDays
) {
    public static ShippingOptionResponse from(ShippingProvider.Option option) {
        return new ShippingOptionResponse(option.code(), option.name(), option.price(),
                option.estimatedDays());
    }
}

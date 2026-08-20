package com.garage.garageapi.shipping.dto;

import com.garage.garageapi.shipping.provider.ShippingProvider;

import java.math.BigDecimal;
import java.util.List;

public record ShippingOptionResponse(
        String code, String name, BigDecimal price, int estimatedDays,
        String provider, List<Leg> legs
) {
    public static ShippingOptionResponse from(ShippingProvider.Option option) {
        return new ShippingOptionResponse(option.code(), option.name(), option.price(),
                option.estimatedDays(), option.provider(), option.legs().stream()
                .map(Leg::from).toList());
    }

    public record Leg(String provider, String code, String name, String originCountry,
                      BigDecimal price, int estimatedDays) {
        static Leg from(ShippingProvider.Leg leg) {
            return new Leg(leg.provider(), leg.code(), leg.name(), leg.originCountry(),
                    leg.priceBrl(), leg.estimatedDays());
        }
    }
}

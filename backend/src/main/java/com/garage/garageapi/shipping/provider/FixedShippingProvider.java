package com.garage.garageapi.shipping.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class FixedShippingProvider implements ShippingProvider {
    public static final String STANDARD_CODE = "STANDARD";
    private static final String STANDARD_NAME = "Entrega padrão";

    private final BigDecimal standardPrice;
    private final int standardEstimatedDays;

    public FixedShippingProvider(
            @Value("${app.shipping.standard.price:18.90}") BigDecimal standardPrice,
            @Value("${app.shipping.standard.estimated-days:8}") int standardEstimatedDays) {
        if (standardPrice == null || standardPrice.signum() < 0) {
            throw new IllegalStateException("Preço do frete padrão deve ser maior ou igual a zero");
        }
        if (standardEstimatedDays < 1) {
            throw new IllegalStateException("Prazo do frete padrão deve ser maior que zero");
        }
        this.standardPrice = standardPrice.setScale(2, RoundingMode.HALF_UP);
        this.standardEstimatedDays = standardEstimatedDays;
    }

    @Override
    public List<Option> quote(Request request) {
        return List.of(new Option(STANDARD_CODE, STANDARD_NAME, standardPrice,
                standardEstimatedDays));
    }
}

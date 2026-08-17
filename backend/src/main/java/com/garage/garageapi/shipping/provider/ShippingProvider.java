package com.garage.garageapi.shipping.provider;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingProvider {
    List<Option> quote(Request request);

    record Request(String zipCode, List<Item> items) { }
    record Item(Long productId, int quantity, BigDecimal unitPrice) { }
    record Option(String code, String name, BigDecimal price, int estimatedDays) { }
}

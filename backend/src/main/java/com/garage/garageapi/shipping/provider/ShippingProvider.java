package com.garage.garageapi.shipping.provider;

import java.math.BigDecimal;
import java.util.List;
import com.garage.garageapi.product.entity.FulfillmentType;

public interface ShippingProvider {
    List<Option> quote(Request request);

    record Request(String zipCode, List<Item> items) { }
    record Item(Long productId, Long productVariantId, int quantity, BigDecimal unitPrice,
                FulfillmentType fulfillmentType, String supplier, String supplierVariantId) {
        public Item(Long productId, int quantity, BigDecimal unitPrice) {
            this(productId, null, quantity, unitPrice, FulfillmentType.LOCAL_STOCK, null, null);
        }
    }
    record Leg(String provider, String code, String name, String originCountry,
               BigDecimal amount, String currency, BigDecimal priceBrl,
               int estimatedDays, List<String> supplierVariantIds) { }
    record Option(String code, String name, BigDecimal price, int estimatedDays,
                  String provider, BigDecimal providerAmount, String providerCurrency,
                  List<Leg> legs) {
        public Option(String code, String name, BigDecimal price, int estimatedDays) {
            this(code, name, price, estimatedDays, "LOCAL", price, "BRL",
                    List.of(new Leg("LOCAL", code, name, "BR", price, "BRL", price,
                            estimatedDays, List.of())));
        }
    }
}

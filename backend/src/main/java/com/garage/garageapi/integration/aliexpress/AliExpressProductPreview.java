package com.garage.garageapi.integration.aliexpress;
import java.math.BigDecimal;
import java.util.*;
public record AliExpressProductPreview(String productId, String name, String description,
        String supplier, String currency, List<String> images, List<Variant> variants,
        Map<String,String> attributes, Map<String,String> packageInfo, Map<String,String> logistics,
        Map<String,String> store, String shipToCountry, String commercialCategory,
        boolean domesticShippingConfirmed, List<String> warnings, String fingerprint) {
    public record Variant(String skuId, String skuCode, String rawSkuAttr, Map<String,String> attributes,
            List<String> images, BigDecimal price, BigDecimal regularPrice, String currency, int availableStock) {}
}

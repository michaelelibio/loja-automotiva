package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.util.*;

/** Only deployment-owned test data. Never constructed from an Order or HTTP input. */
public record AliExpressTestFixture(String id, String authorizationReference, String productId, String skuId,
        String skuAttr, int quantity, String deliveryService, String currency, BigDecimal productCost,
        BigDecimal shippingCost, BigDecimal taxCost, BigDecimal totalCost, Map<String,String> address) {
    public boolean valid() {
        return id != null && id.matches("[a-zA-Z0-9_-]{1,80}") && text(authorizationReference)
                && productId != null && productId.matches("[0-9]{8,30}")
                && skuId != null && skuId.matches("[0-9]{1,30}") && text(skuAttr)
                && quantity > 0 && quantity <= 10 && text(deliveryService) && "BRL".equals(currency)
                && productCost != null && productCost.signum() > 0
                && shippingCost != null && shippingCost.signum() >= 0 && taxCost != null && taxCost.signum() >= 0
                && totalCost != null && productCost.add(shippingCost).add(taxCost).compareTo(totalCost) == 0
                && address != null && "BR".equals(address.get("country"))
                && Set.of("country","province","city","address","address2","zip","contact_person","full_name",
                    "cpf","mobile_no","phone_country","locale").containsAll(address.keySet())
                && List.of("province","city","address","zip","contact_person","full_name","cpf","mobile_no")
                    .stream().allMatch(k -> text(address.get(k)));
    }
    public Map<String,Object> payload() {
        if (!valid()) throw new IllegalStateException("Configuração de teste inválida.");
        return Map.of("logistics_address",Map.copyOf(address),"product_items",List.of(Map.of(
                "product_id",new java.math.BigInteger(productId),"product_count",quantity,
                "sku_attr",skuAttr,"logistics_service_name",deliveryService)));
    }
    public String digest() {
        try {
            // Stable even if JSON object key ordering differs across restarts.
            var values=Arrays.asList(id,authorizationReference,productId,skuId,skuAttr,quantity,deliveryService,currency,
                    productCost.toPlainString(),shippingCost.toPlainString(),taxCost.toPlainString(),totalCost.toPlainString(),new TreeMap<>(address));
            byte[] bytes=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(values);
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static boolean text(String s) { return s != null && !s.isBlank() && s.length() <= 255; }
}

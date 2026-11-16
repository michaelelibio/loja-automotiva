package com.garage.garageapi.product.entity;

import java.util.Objects;

/** Shared technical checks for the public catalog, eligibility and checkout. */
public final class AliExpressPurchasability {
    private AliExpressPurchasability() { }

    public static boolean applies(Product product) {
        return product.getFulfillmentType() == FulfillmentType.DROPSHIPPING
                && "ALIEXPRESS".equalsIgnoreCase(product.getSupplier());
    }

    public static boolean hasSelection(ProductVariant variant) {
        // null means unverified/missing. Empty is deliberately persisted only for
        // a provider-confirmed single SKU without selection attributes.
        String key = variant.getRawVariantKey();
        if(key==null)return false;
        if(key.isEmpty())return variant.getAttributes().isEmpty();
        if(key.isBlank())return false;
        for(var entry:variant.getAttributes().entrySet()) {
            if(!entry.getKey().matches("[0-9]+:valueId"))continue;
            String pair=entry.getKey().substring(0,entry.getKey().indexOf(':'))+":"+entry.getValue();
            if(java.util.Arrays.stream(key.split(";")).noneMatch(value->value.equals(pair)||value.startsWith(pair+"#")))return false;
        }
        return true;
    }

    public static boolean canPurchase(Product product, ProductVariant variant, int quantity) {
        return quantity > 0 && Boolean.TRUE.equals(product.getActive())
                && product.getPrice() != null && product.getPrice().signum() > 0
                && variant != null && Boolean.TRUE.equals(variant.getActive())
                && "ALIEXPRESS".equalsIgnoreCase(variant.getSupplier())
                && Objects.equals(product.getId(), variant.getProduct().getId())
                && Objects.equals(product.getSupplierProductId(), variant.getSupplierProductId())
                && variant.getSupplierVariantId() != null && !variant.getSupplierVariantId().isBlank()
                && hasSelection(variant) && variant.getSupplierStockQuantity() != null
                && variant.getSupplierStockQuantity() >= quantity;
    }
}

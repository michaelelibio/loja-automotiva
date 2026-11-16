package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.ProductVariant;

import java.util.Map;

public record ProductVariantResponse(Long id, String name, String sku,
                                     Map<String, String> attributes, String imageUrl,
                                     java.math.BigDecimal price, Integer stockQuantity, boolean availableForSale) {
    public static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(variant.getId(), variant.getName(),
                variant.getSupplierSku(), variant.getAttributes(), variant.getImageUrl(), variant.getProduct().getPrice(),
                com.garage.garageapi.product.entity.AliExpressPurchasability.applies(variant.getProduct())
                    ? variant.getSupplierStockQuantity() : variant.getProduct().requiresLocalStock() ? variant.getProduct().getStockQuantity() : null,
                com.garage.garageapi.product.entity.AliExpressPurchasability.applies(variant.getProduct())
                    ? com.garage.garageapi.product.entity.AliExpressPurchasability.canPurchase(variant.getProduct(),variant,1)
                    : variant.getProduct().isAvailableForSale());
    }
}

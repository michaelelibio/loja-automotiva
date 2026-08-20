package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.ProductVariant;

import java.util.Map;

public record ProductVariantResponse(Long id, String name, String sku,
                                     Map<String, String> attributes, String imageUrl) {
    public static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(variant.getId(), variant.getName(),
                variant.getSupplierSku(), variant.getAttributes(), variant.getImageUrl());
    }
}

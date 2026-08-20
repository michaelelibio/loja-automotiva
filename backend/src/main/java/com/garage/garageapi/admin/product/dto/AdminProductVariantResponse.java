package com.garage.garageapi.admin.product.dto;

import com.garage.garageapi.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AdminProductVariantResponse(
        Long id,
        String supplier,
        String supplierVariantId,
        String supplierProductId,
        String supplierSku,
        String name,
        Map<String, String> attributes,
        BigDecimal supplierCost,
        String supplierCostCurrency,
        String imageUrl,
        BigDecimal weightGrams,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal heightMm,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminProductVariantResponse from(ProductVariant variant) {
        return new AdminProductVariantResponse(
                variant.getId(), variant.getSupplier(), variant.getSupplierVariantId(),
                variant.getSupplierProductId(), variant.getSupplierSku(), variant.getName(),
                variant.getAttributes(), variant.getSupplierCost(),
                variant.getSupplierCostCurrency(), variant.getImageUrl(), variant.getWeightGrams(),
                variant.getLengthMm(), variant.getWidthMm(), variant.getHeightMm(),
                variant.getActive(), variant.getCreatedAt(), variant.getUpdatedAt());
    }
}
package com.garage.garageapi.integration.cj.dto;

import com.garage.garageapi.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record CjProductImportResponse(
        Long id,
        String name,
        String slug,
        String sku,
        String imageUrl,
        String supplier,
        String supplierProductId,
        BigDecimal supplierCostUsd,
        BigDecimal supplierExchangeRate,
        Instant supplierCostUpdatedAt,
        BigDecimal costPrice,
        BigDecimal price,
        String category,
        Integer stock,
        Boolean active
) {
    public static CjProductImportResponse from(Product product) {
        return new CjProductImportResponse(
                product.getId(), product.getName(), product.getSlug(), product.getSku(),
                product.getImageUrl(), product.getSupplier(), product.getSupplierProductId(),
                product.getSupplierCostUsd(), product.getSupplierExchangeRate(),
                product.getSupplierCostUpdatedAt(),
                product.getCostPrice(), product.getPrice(), product.getCategory(),
                product.getStockQuantity(), product.getActive());
    }
}

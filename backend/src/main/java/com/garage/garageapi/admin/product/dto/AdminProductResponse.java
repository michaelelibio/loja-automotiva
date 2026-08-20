package com.garage.garageapi.admin.product.dto;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductType;
import com.garage.garageapi.product.entity.FulfillmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminProductResponse(
        Long id, String name, String slug, String description, String longDescription,
        BigDecimal price, BigDecimal oldPrice, BigDecimal costPrice, Integer stock,
        Boolean active, String category, String sku, String imageUrl, ProductType productType,
        String supplier, String supplierProductId, BigDecimal supplierCostUsd,
        BigDecimal supplierExchangeRate, Instant supplierCostUpdatedAt,
        FulfillmentType fulfillmentType, Boolean availableForSale,
        Instant createdAt, Instant updatedAt,
        List<AdminProductVariantResponse> variants
) {
    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(product.getId(), product.getName(), product.getSlug(),
                product.getDescription(), product.getLongDescription(), product.getPrice(),
                product.getOldPrice(), product.getCostPrice(), product.getStockQuantity(),
                product.getActive(), product.getCategory(), product.getSku(), product.getImageUrl(),
                product.getProductType(), product.getSupplier(), product.getSupplierProductId(),
                product.getSupplierCostUsd(), product.getSupplierExchangeRate(),
                product.getSupplierCostUpdatedAt(), product.getFulfillmentType(),
                product.isAvailableForSale(), product.getCreatedAt(), product.getUpdatedAt(),
                product.getVariants().stream().map(AdminProductVariantResponse::from).toList());
    }
}

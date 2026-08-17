package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductType;
import com.garage.garageapi.product.entity.FulfillmentType;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        String longDescription,
        BigDecimal price,
        BigDecimal oldPrice,
        String category,
        Integer stockQuantity,
        String imageUrl,
        Boolean active,
        ProductType productType,
        FulfillmentType fulfillmentType,
        Boolean availableForSale
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getSlug(), product.getDescription(),
                product.getLongDescription(), product.getPrice(), product.getOldPrice(), product.getCategory(),
                product.getStockQuantity(), product.getImageUrl(), product.getActive(), product.getProductType(),
                product.getFulfillmentType(), product.isAvailableForSale()
        );
    }
}

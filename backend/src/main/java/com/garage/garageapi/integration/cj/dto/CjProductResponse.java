package com.garage.garageapi.integration.cj.dto;

import java.math.BigDecimal;
import java.util.List;

public record CjProductResponse(
        int page,
        int size,
        long totalRecords,
        long totalPages,
        List<Product> products
) {
    public record Product(
            String cjProductId,
            String name,
            String imageUrl,
            List<String> imageUrls,
            String productKeyEn,
            BigDecimal priceUsd,
            String categoryId,
            String categoryName,
            String sku
    ) {}
}

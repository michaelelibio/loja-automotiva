package com.garage.garageapi.integration.cj.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CjProductVariantsResponse(
        String productId,
        List<Variant> variants
) {
    public record Variant(
            String cjVariantId,
            String cjProductId,
            String sku,
            String name,
            BigDecimal priceUsd,
            String imageUrl,
            Map<String, String> attributes,
            String variantStandard,
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm,
            BigDecimal volumeMm3,
            BigDecimal weightGrams
    ) {}
}
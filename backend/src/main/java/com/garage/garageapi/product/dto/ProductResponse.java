package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductType;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.entity.ProductMedia;

import java.math.BigDecimal;
import java.util.List;
import java.util.Comparator;

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
        Boolean availableForSale,
        Boolean requiresVariantSelection,
        List<ProductVariantResponse> variants,
        List<ProductMediaResponse> media
) {
    public static ProductResponse from(Product product) {
        List<ProductVariantResponse> activeVariants = product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
                .map(ProductVariantResponse::from).toList();
        return new ProductResponse(
                product.getId(), product.getName(), product.getSlug(), product.getDescription(),
                product.getLongDescription(), product.getPrice(), product.getOldPrice(), product.getCategory(),
                product.getStockQuantity(), product.getImageUrl(), product.getActive(), product.getProductType(),
                product.getFulfillmentType(), product.isAvailableForSale(),
                !product.getVariants().isEmpty(), activeVariants,
                product.getMedia().stream().filter(media -> Boolean.TRUE.equals(media.getActive()))
                        .sorted(Comparator.comparing(ProductMedia::getPosition)
                                .thenComparing(ProductMedia::getId,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(ProductMediaResponse::from).toList()
        );
    }
}

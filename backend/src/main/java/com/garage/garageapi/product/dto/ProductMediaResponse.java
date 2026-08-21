package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.ProductMedia;
import com.garage.garageapi.product.entity.ProductMediaType;

public record ProductMediaResponse(Long id, ProductMediaType type, String url,
                                   Integer position, String altText) {
    public static ProductMediaResponse from(ProductMedia media) {
        return new ProductMediaResponse(media.getId(), media.getType(), media.getUrl(),
                media.getPosition(), media.getAltText());
    }
}

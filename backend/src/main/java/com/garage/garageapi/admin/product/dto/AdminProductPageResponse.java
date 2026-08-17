package com.garage.garageapi.admin.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminProductPageResponse(
        List<AdminProductResponse> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last
) {
    public static AdminProductPageResponse from(Page<AdminProductResponse> products) {
        return new AdminProductPageResponse(products.getContent(), products.getNumber(),
                products.getSize(), products.getTotalElements(), products.getTotalPages(),
                products.isFirst(), products.isLast());
    }
}

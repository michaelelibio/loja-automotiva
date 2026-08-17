package com.garage.garageapi.admin.stock.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminStockMovementPageResponse(
        List<AdminStockMovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminStockMovementPageResponse from(Page<AdminStockMovementResponse> page) {
        return new AdminStockMovementPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

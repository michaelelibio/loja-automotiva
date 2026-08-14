package com.garage.garageapi.admin.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminOrderPageResponse(
        List<AdminOrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminOrderPageResponse from(Page<AdminOrderSummaryResponse> result) {
        return new AdminOrderPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}

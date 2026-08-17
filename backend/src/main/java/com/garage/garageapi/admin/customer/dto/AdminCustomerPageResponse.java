package com.garage.garageapi.admin.customer.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminCustomerPageResponse(
        List<AdminCustomerSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminCustomerPageResponse from(Page<AdminCustomerSummaryResponse> page) {
        return new AdminCustomerPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

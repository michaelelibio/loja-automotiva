package com.garage.garageapi.admin.customer.dto;

import com.garage.garageapi.user.entity.AuthProvider;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminCustomerSummaryResponse(
        Long id,
        String name,
        String email,
        AuthProvider authProvider,
        boolean active,
        boolean emailVerified,
        Instant createdAt,
        long totalOrders,
        long confirmedOrders,
        BigDecimal totalSpent,
        BigDecimal averageTicket,
        Instant lastOrderAt
) { }

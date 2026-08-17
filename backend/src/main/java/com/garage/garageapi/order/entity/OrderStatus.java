package com.garage.garageapi.order.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELED,
    EXPIRED;

    private static final Set<OrderStatus> CONFIRMED_REVENUE_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(PAID, PROCESSING, SHIPPED, DELIVERED));

    public static Set<OrderStatus> confirmedRevenueStatuses() {
        return CONFIRMED_REVENUE_STATUSES;
    }
}

package com.garage.garageapi.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELED,
    EXPIRED
}

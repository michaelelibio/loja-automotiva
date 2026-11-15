package com.garage.garageapi.payment.entity;

public enum PaymentStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELED,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CHARGED_BACK,
    DISPUTED;

    public boolean requiresFinancialReview() {
        return this == PARTIALLY_REFUNDED || this == REFUNDED || this == CHARGED_BACK
                || this == DISPUTED || this == CANCELED;
    }
}

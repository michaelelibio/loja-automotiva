package com.garage.garageapi.payment.dto;

import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, PaymentMethod method, PaymentStatus status,
                              String providerPaymentId, String qrCode, String qrCodeBase64,
                              Instant expiresAt, Instant paidAt, Instant createdAt, Instant updatedAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getMethod(),
                payment.getStatus(), payment.getProviderPaymentId(), payment.getQrCode(),
                payment.getQrCodeBase64(), payment.getExpiresAt(), payment.getPaidAt(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}

package com.garage.garageapi.payment.gateway;

import com.garage.garageapi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public interface PixPaymentGateway {
    Result create(Request request);
    Result find(String providerOrderId);

    record Request(Long orderId, Long paymentId, BigDecimal amount, String payerEmail,
                   String idempotencyKey) { }

    record Result(String providerOrderId, String providerPaymentId, String externalReference,
                  PaymentStatus status, String qrCode, String qrCodeBase64,
                  Instant expiresAt, Instant paidAt) {
        public Result(String providerPaymentId, PaymentStatus status, String qrCode,
                      String qrCodeBase64, Instant expiresAt, Instant paidAt) {
            this(null, providerPaymentId, null, status, qrCode, qrCodeBase64, expiresAt, paidAt);
        }
    }
}

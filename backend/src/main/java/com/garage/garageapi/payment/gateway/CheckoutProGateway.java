package com.garage.garageapi.payment.gateway;

import com.garage.garageapi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CheckoutProGateway {
    PreferenceResult createPreference(PreferenceRequest request);
    PaymentResult findPayment(String providerPaymentId);

    record Item(String id, String title, int quantity, BigDecimal unitPrice) { }

    record PreferenceRequest(Long orderId, Long paymentId, BigDecimal total, String payerName,
                             String payerEmail, String idempotencyKey, List<Item> items,
                             BigDecimal shippingCost, String shippingName) { }

    record PreferenceResult(String preferenceId, String externalReference, String checkoutUrl) { }

    record PaymentResult(String providerPaymentId, String externalReference, PaymentStatus status,
                         String statusDetail, BigDecimal transactionAmount, String currencyId,
                         String paymentType, String paymentMethodId, Instant approvedAt) { }
}

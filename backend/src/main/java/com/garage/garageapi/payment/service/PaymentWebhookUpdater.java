package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.PixPaymentGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookUpdater {
    private final PaymentRepository paymentRepository;

    public PaymentWebhookUpdater(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void apply(String notifiedProviderOrderId, PixPaymentGateway.Result providerResult) {
        Payment payment = paymentRepository.findByProviderOrderIdForUpdate(notifiedProviderOrderId)
                .orElse(null);
        if (payment == null || !matches(payment, notifiedProviderOrderId, providerResult)) return;

        payment.synchronizeProviderStatus(providerResult.status(), providerResult.paidAt());
        if (providerResult.status() == PaymentStatus.PAID) {
            Order order = payment.getOrder();
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) order.markPaid();
        }
    }

    private boolean matches(Payment payment, String notifiedProviderOrderId,
                            PixPaymentGateway.Result result) {
        if (!notifiedProviderOrderId.equals(result.providerOrderId())) return false;
        if (result.providerPaymentId() != null
                && !result.providerPaymentId().equals(payment.getProviderPaymentId())) return false;
        String expectedReference = "garage_order_" + payment.getOrder().getId()
                + "_payment_" + payment.getId();
        return result.externalReference() == null
                || expectedReference.equals(result.externalReference());
    }
}

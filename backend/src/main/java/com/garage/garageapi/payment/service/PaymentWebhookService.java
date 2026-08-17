package com.garage.garageapi.payment.service;

import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {
    private final CheckoutProGateway checkoutProGateway;
    private final CheckoutProWebhookUpdater checkoutProUpdater;

    public PaymentWebhookService(CheckoutProGateway checkoutProGateway,
                                 CheckoutProWebhookUpdater checkoutProUpdater) {
        this.checkoutProGateway = checkoutProGateway;
        this.checkoutProUpdater = checkoutProUpdater;
    }

    public void processPaymentNotification(String providerPaymentId) {
        var providerResult = checkoutProGateway.findPayment(providerPaymentId);
        checkoutProUpdater.apply(providerPaymentId, providerResult);
    }
}

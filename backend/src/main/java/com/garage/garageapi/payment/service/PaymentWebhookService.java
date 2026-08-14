package com.garage.garageapi.payment.service;

import com.garage.garageapi.payment.gateway.PixPaymentGateway;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {
    private final PixPaymentGateway pixPaymentGateway;
    private final PaymentWebhookUpdater updater;

    public PaymentWebhookService(PixPaymentGateway pixPaymentGateway, PaymentWebhookUpdater updater) {
        this.pixPaymentGateway = pixPaymentGateway;
        this.updater = updater;
    }

    public void processOrderNotification(String providerOrderId) {
        PixPaymentGateway.Result providerResult = pixPaymentGateway.find(providerOrderId);
        updater.apply(providerOrderId, providerResult);
    }
}

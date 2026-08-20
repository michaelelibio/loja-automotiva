package com.garage.garageapi.order.fulfillment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderFulfillmentNotificationService {
    private final CjFulfillmentService fulfillmentService;

    public OrderFulfillmentNotificationService(CjFulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    public void afterCommit(Long orderId) {
        Runnable action = () -> fulfillmentService.fulfill(orderId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }
}

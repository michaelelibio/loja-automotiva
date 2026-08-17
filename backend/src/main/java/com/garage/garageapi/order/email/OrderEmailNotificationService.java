package com.garage.garageapi.order.email;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderEmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(OrderEmailNotificationService.class);
    private final OrderEmailService emailService;
    public OrderEmailNotificationService(OrderEmailService emailService) { this.emailService = emailService; }

    public void afterCommit(Order order, OrderStatus status) {
        OrderEmailDetails details = OrderEmailDetails.from(order);
        Runnable delivery = () -> deliver(details, status);
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delivery.run(); }
            });
        } else delivery.run();
    }

    private void deliver(OrderEmailDetails order, OrderStatus status) {
        try {
            switch (status) {
                case PAID -> emailService.sendPaymentApproved(order);
                case PROCESSING -> emailService.sendOrderProcessing(order);
                case SHIPPED -> emailService.sendOrderShipped(order);
                case DELIVERED -> emailService.sendOrderDelivered(order);
                default -> { }
            }
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar e-mail de pedido; orderId={}; status={}", order.orderId(), status);
        }
    }
}

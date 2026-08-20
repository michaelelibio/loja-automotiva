package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.order.entity.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CjFulfillmentStateService {
    private final OrderFulfillmentRepository repository;

    public CjFulfillmentStateService(OrderFulfillmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long orderId) {
        OrderFulfillment fulfillment = repository.findByOrderIdForUpdate(orderId).orElse(null);
        if (fulfillment == null || fulfillment.getOrder().getStatus() != OrderStatus.PAID) return null;
        String token = fulfillment.claim(Instant.now());
        return token == null ? null : new Claim(fulfillment.getId(), token);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long orderId, String token, String supplierOrderId,
                         String supplierShipmentOrderId) {
        repository.findByOrderIdForUpdate(orderId).ifPresent(fulfillment ->
                fulfillment.complete(token, supplierOrderId, supplierShipmentOrderId, Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long orderId, String token, String error) {
        repository.findByOrderIdForUpdate(orderId).ifPresent(fulfillment ->
                fulfillment.fail(token, sanitize(error), Instant.now()));
    }

    private String sanitize(String error) {
        String message = error == null || error.isBlank()
                ? "Falha não identificada ao criar pedido no fornecedor" : error.trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record Claim(Long fulfillmentId, String token) { }
}

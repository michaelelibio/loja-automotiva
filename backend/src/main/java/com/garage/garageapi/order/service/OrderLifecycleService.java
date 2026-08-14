package com.garage.garageapi.order.service;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class OrderLifecycleService {
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Autowired
    public OrderLifecycleService(OrderRepository orderRepository) {
        this(orderRepository, Clock.systemUTC());
    }

    OrderLifecycleService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Transactional
    public Order transition(Long orderId, OrderStatus target) {
        Order order = orderRepository.findByIdForLifecycleUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nÃ£o encontrado: " + orderId));
        Instant occurredAt = clock.instant();
        switch (target) {
            case PROCESSING -> order.startProcessing(occurredAt);
            case SHIPPED -> order.markShipped(occurredAt);
            case DELIVERED -> order.markDelivered(occurredAt);
            case CANCELED -> order.cancel();
            case PAID -> throw conflict("PAID Ã© definido exclusivamente pelo fluxo de pagamento");
            case EXPIRED -> throw conflict("EXPIRED nÃ£o pode ser definido manualmente");
            case PENDING_PAYMENT -> throw conflict("NÃ£o Ã© permitido regredir para PENDING_PAYMENT");
        }
        return order;
    }

    private ResourceConflictException conflict(String reason) {
        return new ResourceConflictException("TransiÃ§Ã£o manual de pedido invÃ¡lida: " + reason);
    }
}

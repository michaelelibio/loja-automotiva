package com.garage.garageapi.admin.order;

import com.garage.garageapi.admin.order.dto.AdminOrderPageResponse;
import com.garage.garageapi.admin.order.dto.AdminOrderResponse;
import com.garage.garageapi.admin.order.dto.AdminOrderSummaryResponse;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.service.OrderLifecycleService;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AdminOrderService {
    private static final Set<OrderStatus> MANUAL_STATUSES =
            EnumSet.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderLifecycleService orderLifecycleService;

    public AdminOrderService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                             OrderLifecycleService orderLifecycleService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderLifecycleService = orderLifecycleService;
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse list(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Order> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : orderRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status, pageable);
        return AdminOrderPageResponse.from(orders.map(AdminOrderSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse get(Long orderId) {
        return response(find(orderId));
    }

    @Transactional
    public AdminOrderResponse transition(Long orderId, OrderStatus target) {
        if (!MANUAL_STATUSES.contains(target)) {
            throw new ResourceConflictException(
                    "Status não permitido para alteração administrativa: " + target);
        }
        return response(orderLifecycleService.transition(orderId, target));
    }

    private Order find(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + orderId));
    }

    private AdminOrderResponse response(Order order) {
        Payment payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
                .orElse(null);
        return AdminOrderResponse.from(order, payment);
    }
}

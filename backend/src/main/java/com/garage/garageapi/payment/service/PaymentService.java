package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.dto.CreatePaymentRequest;
import com.garage.garageapi.payment.dto.PaymentResponse;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.gateway.PaymentProviderException;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final PaymentAttemptService attemptService;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          UserService userService, PaymentAttemptService attemptService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.attemptService = attemptService;
    }

    public PaymentResponse create(Jwt jwt, Long orderId, CreatePaymentRequest request) {
        User user = userService.findCurrentUser(jwt);
        PaymentAttemptService.PreparedAttempt attempt =
                attemptService.prepare(user.getId(), orderId);
        if (attempt.completed()) return PaymentResponse.from(attemptService.find(attempt.paymentId()));
        try {
            CheckoutProGateway.PreferenceRequest checkoutRequest =
                    new CheckoutProGateway.PreferenceRequest(attempt.orderId(), attempt.paymentId(),
                            attempt.amount(), attempt.payerName(), attempt.payerEmail(),
                            attempt.idempotencyKey(), attempt.items(), attempt.shippingCost(),
                            attempt.shippingName());
            return PaymentResponse.from(attemptService.createCheckoutPreference(
                    attempt.paymentId(), checkoutRequest));
        } catch (PaymentProviderException exception) {
            if (exception.isDefinitiveRejection()) {
                attemptService.failDefinitivelyRejectedAttempt(attempt.paymentId());
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(Jwt jwt, Long orderId) {
        Order order = ownedOrder(jwt, orderId);
        return paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(order.getId()).stream()
                .map(PaymentResponse::from).toList();
    }

    private Order ownedOrder(Jwt jwt, Long orderId) {
        User user = userService.findCurrentUser(jwt);
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + orderId));
    }
}

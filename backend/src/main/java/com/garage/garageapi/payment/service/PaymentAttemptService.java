package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.PixPaymentGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentAttemptService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentAttemptService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public PreparedAttempt prepare(Long userId, Long orderId, PaymentMethod method) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException("Pedido não está disponível para pagamento");
        }
        if (!Instant.now().isBefore(order.getExpiresAt())) {
            throw new ResourceConflictException("Prazo para pagamento do pedido expirou");
        }
        Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(orderId, PaymentStatus.PENDING)
                .orElseGet(() -> paymentRepository.saveAndFlush(new Payment(order, method)));
        if (payment.getMethod() != method) {
            throw new ResourceConflictException("Já existe uma tentativa de pagamento pendente");
        }
        payment.ensureIdempotencyKey();
        return new PreparedAttempt(payment.getId(), order.getId(), order.getTotal(),
                order.getUser().getEmail(), payment.getIdempotencyKey(),
                payment.getProviderPaymentId() != null);
    }

    @Transactional
    public Payment complete(Long paymentId, PixPaymentGateway.Result result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tentativa de pagamento não encontrada"));
        payment.applyProviderResult(result.providerOrderId(), result.providerPaymentId(),
                result.status(), result.qrCode(),
                result.qrCodeBase64(), result.expiresAt(), result.paidAt());
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public void failDefinitivelyRejectedAttempt(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tentativa de pagamento não encontrada"));
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailed();
            paymentRepository.saveAndFlush(payment);
        }
    }

    @Transactional(readOnly = true)
    public Payment find(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tentativa de pagamento não encontrada"));
    }

    public record PreparedAttempt(Long paymentId, Long orderId, BigDecimal amount, String payerEmail,
                                  String idempotencyKey, boolean completed) { }
}

package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentAttemptService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.garage.garageapi.shipping.commercial.CommercialShippingService commercial;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CheckoutProGateway checkoutProGateway;
    private final boolean paymentCreationEnabled;

    public PaymentAttemptService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                                 CheckoutProGateway checkoutProGateway,
                                 @Value("${app.payment.creation-enabled:false}") boolean paymentCreationEnabled) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.checkoutProGateway = checkoutProGateway;
        this.paymentCreationEnabled = paymentCreationEnabled;
    }

    @Transactional
    public PreparedAttempt prepare(Long userId, Long orderId) {
        requirePaymentCreationEnabled();
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException("Pedido não está disponível para pagamento");
        }
        if (!Instant.now().isBefore(order.getExpiresAt())) {
            throw new ResourceConflictException("Prazo para pagamento do pedido expirou");
        }
        com.garage.garageapi.integration.aliexpress.AliExpressFreightService.requireConfirmedFreight(order);
        if(order.getCommercialShipping()!=null) commercial.revalidate(order);
        Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(orderId, PaymentStatus.PENDING)
                .orElseGet(() -> paymentRepository.saveAndFlush(
                        new Payment(order, PaymentMethod.MERCADO_PAGO)));
        if (payment.getMethod() != PaymentMethod.MERCADO_PAGO) {
            throw new ResourceConflictException("Já existe uma tentativa de pagamento pendente");
        }
        payment.ensureIdempotencyKey();
        List<CheckoutProGateway.Item> items = order.getItems().stream()
                .map(item -> new CheckoutProGateway.Item(String.valueOf(item.getProductId()),
                        item.getProductName(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        boolean completed = payment.getProviderPreferenceId() != null;
        return new PreparedAttempt(payment.getId(), order.getId(), order.getTotal(),
                order.getUser().getName(), order.getUser().getEmail(), payment.getIdempotencyKey(),
                items, order.getShippingCost(), order.getShippingName(), completed,
                order.getAssistedQuote()==null && order.getCommercialShipping()==null ? null : order.getExpiresAt());
    }

    @Transactional
    public Payment createCheckoutPreference(Long paymentId,
                                            CheckoutProGateway.PreferenceRequest request) {
        requirePaymentCreationEnabled();
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tentativa de pagamento não encontrada"));
        com.garage.garageapi.integration.aliexpress.AliExpressFreightService.requireConfirmedFreight(payment.getOrder());
        if(payment.getOrder().getCommercialShipping()!=null) commercial.revalidate(payment.getOrder());
        if (payment.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT
                || !Instant.now().isBefore(payment.getOrder().getExpiresAt())
                || request.total().compareTo(payment.getOrder().getTotal()) != 0)
            throw new ResourceConflictException("Pedido alterado ou expirado antes da cobrança.");
        if (payment.getProviderPreferenceId() != null) return payment;
        CheckoutProGateway.PreferenceResult result = checkoutProGateway.createPreference(request);
        payment.applyCheckoutPreference(result.preferenceId(), result.externalReference(),
                result.checkoutUrl());
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

    public record PreparedAttempt(Long paymentId, Long orderId, BigDecimal amount, String payerName,
                                  String payerEmail, String idempotencyKey,
                                  List<CheckoutProGateway.Item> items, BigDecimal shippingCost,
                                  String shippingName, boolean completed, Instant expiresAt) { }

    private void requirePaymentCreationEnabled() {
        if (!paymentCreationEnabled) {
            throw new ResourceConflictException("Pagamentos temporariamente indisponíveis durante a validação da loja.");
        }
    }
}

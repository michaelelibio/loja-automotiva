package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.email.OrderEmailNotificationService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentNotificationService;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CheckoutProWebhookUpdater {
    private final PaymentRepository paymentRepository;
    private final OrderEmailNotificationService emailNotificationService;
    private final OrderFulfillmentNotificationService fulfillmentNotificationService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderRepository orderRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.garage.garageapi.integration.aliexpress.AliExpressDispatchStore dispatchStore;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.garage.garageapi.payment.repository.PaymentFinancialEventRepository financialEvents;

    public CheckoutProWebhookUpdater(PaymentRepository paymentRepository,
                                     OrderEmailNotificationService emailNotificationService,
                                     OrderFulfillmentNotificationService fulfillmentNotificationService) {
        this.paymentRepository = paymentRepository;
        this.emailNotificationService = emailNotificationService;
        this.fulfillmentNotificationService = fulfillmentNotificationService;
    }

    @Transactional
    public void apply(String notifiedPaymentId, CheckoutProGateway.PaymentResult result) {
        if (result == null || !StringUtils.hasText(result.externalReference())) return;
        // Same lock order as dispatch/admin transitions. Resolve the scalar key without locking payment first.
        if (orderRepository != null) {
            Long orderId = paymentRepository.findOrderIdByExternalReference(result.externalReference()).orElse(null);
            if (orderId == null || orderRepository.findByIdForLifecycleUpdate(orderId).isEmpty()) return;
        }
        Payment payment = paymentRepository.findByExternalReferenceForUpdate(result.externalReference())
                .orElse(null);
        if (payment == null || !matches(payment, notifiedPaymentId, result)) return;
        Order order = payment.getOrder();
        if (payment.getProviderPaymentId() != null && !payment.getProviderPaymentId().equals(result.providerPaymentId())) {
            // A Checkout Pro preference may have multiple payment IDs. Never silently overwrite a capture.
            order.recordPaymentBindingConflict();
            return;
        }
        var disposition = payment.synchronizeProviderSnapshot(result, java.time.Instant.now());
        if (disposition == Payment.SnapshotDisposition.STALE) return;
        if (disposition == Payment.SnapshotDisposition.INVALID) {
            order.requirePaymentReview();
            return;
        }
        if (disposition == Payment.SnapshotDisposition.APPLIED && financialEvents != null)
            financialEvents.save(new com.garage.garageapi.payment.entity.PaymentFinancialEvent(payment,
                    result.transactionAmount(), java.time.Instant.now()));
        order.applyPaymentFinancialState(payment);
        if (payment.getStatus() != PaymentStatus.PAID && payment.getPaidAt() != null
                && payment.getStatus().requiresFinancialReview() && order.getStatus() == OrderStatus.PENDING_PAYMENT)
            order.markPaid(); // Captured funds were reversed/disputed; financial state remains explicit and blocks fulfillment.
        if (payment.getStatus() == PaymentStatus.PAID && !order.isPaymentReviewRequired()) {
            var quote = order.getAssistedQuote();
            boolean refundRequired = quote!=null && !"REFUNDED".equals(quote.state())
                    && (!"ACCEPTED".equals(quote.state()) || result.approvedAt()==null || quote.validUntil()==null
                        || !result.approvedAt().isBefore(quote.validUntil())
                        || order.getStatus()==OrderStatus.EXPIRED || order.getStatus()==OrderStatus.CANCELED);
            boolean newlyPaid=order.getStatus() == OrderStatus.PENDING_PAYMENT;
            if (newlyPaid) {
                order.markPaid();
                emailNotificationService.afterCommit(order, OrderStatus.PAID);
            }
            if (refundRequired) order.holdAssistedPaymentForRefund();
            boolean aliExpress=order.getItems().stream().anyMatch(i->"ALIEXPRESS".equalsIgnoreCase(i.getSupplier()));
            if (aliExpress && dispatchStore != null && dispatchStore.find(order.getId()) != null) {
                var row = dispatchStore.locked(order);
                row.clearPaymentDispatchBlock();
                dispatchStore.flush(row);
            }
            if (!refundRequired && (newlyPaid || aliExpress && Boolean.TRUE.equals(result.liveMode()) && result.approvedAt()!=null)
                    && (order.getStatus()==OrderStatus.PAID || order.getStatus()==OrderStatus.PROCESSING))
                fulfillmentNotificationService.afterCommit(order.getId());
        }
    }

    private boolean matches(Payment payment, String notifiedPaymentId,
                            CheckoutProGateway.PaymentResult result) {
        if (payment.getMethod() != PaymentMethod.MERCADO_PAGO) return false;
        if (notifiedPaymentId == null || !notifiedPaymentId.equals(result.providerPaymentId())) return false;
        String expected = "garage_order_" + payment.getOrder().getId() + "_payment_" + payment.getId();
        if (!expected.equals(result.externalReference())) return false;
        if (!"BRL".equalsIgnoreCase(result.currencyId())) return false;
        return result.transactionAmount() != null
                && payment.getOrder().getTotal().compareTo(result.transactionAmount()) == 0;
    }
}

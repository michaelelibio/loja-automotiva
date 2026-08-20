package com.garage.garageapi.payment.service;

import com.garage.garageapi.order.email.OrderEmailNotificationService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
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

    public CheckoutProWebhookUpdater(PaymentRepository paymentRepository,
                                     OrderEmailNotificationService emailNotificationService,
                                     OrderFulfillmentNotificationService fulfillmentNotificationService) {
        this.paymentRepository = paymentRepository;
        this.emailNotificationService = emailNotificationService;
        this.fulfillmentNotificationService = fulfillmentNotificationService;
    }

    @Transactional
    public void apply(String notifiedPaymentId, CheckoutProGateway.PaymentResult result) {
        if (!StringUtils.hasText(result.externalReference())) return;
        Payment payment = paymentRepository.findByExternalReferenceForUpdate(result.externalReference())
                .orElse(null);
        if (payment == null || !matches(payment, notifiedPaymentId, result)) return;

        payment.synchronizeCheckoutPayment(result.providerPaymentId(), result.status(),
                result.approvedAt(), result.paymentType(), result.paymentMethodId());
        if (result.status() == PaymentStatus.PAID) {
            Order order = payment.getOrder();
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.markPaid();
                emailNotificationService.afterCommit(order, OrderStatus.PAID);
                fulfillmentNotificationService.afterCommit(order.getId());
            }
        }
    }

    private boolean matches(Payment payment, String notifiedPaymentId,
                            CheckoutProGateway.PaymentResult result) {
        if (payment.getMethod() != PaymentMethod.MERCADO_PAGO) return false;
        if (!notifiedPaymentId.equals(result.providerPaymentId())) return false;
        if (payment.getProviderPaymentId() != null
                && !payment.getProviderPaymentId().equals(result.providerPaymentId())) return false;
        String expected = "garage_order_" + payment.getOrder().getId() + "_payment_" + payment.getId();
        if (!expected.equals(result.externalReference())) return false;
        if (!"BRL".equalsIgnoreCase(result.currencyId())) return false;
        return result.transactionAmount() != null
                && payment.getOrder().getTotal().compareTo(result.transactionAmount()) == 0;
    }
}

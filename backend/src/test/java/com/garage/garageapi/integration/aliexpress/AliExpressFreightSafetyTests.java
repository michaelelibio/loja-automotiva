package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.service.PaymentAttemptService;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressFreightSafetyTests {
    final OrderRepository orders = mock(OrderRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final CheckoutProGateway gateway = mock(CheckoutProGateway.class);
    final PaymentAttemptService service = new PaymentAttemptService(payments, orders, gateway, true);

    private Order order() {
        Order order = mock(Order.class);
        OrderItem item = mock(OrderItem.class);
        when(item.getSupplier()).thenReturn("ALIEXPRESS");
        when(order.getItems()).thenReturn(List.of(item));
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
        when(order.getExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
        return order;
    }

    @Test void unpaidLegacyOrderCannotStartPaymentWithoutConfirmedFreight() {
        Order order = order();
        when(orders.findByIdAndUserIdForUpdate(1L, 2L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.prepare(2L, 1L)).isInstanceOf(ResourceConflictException.class)
                .hasMessage(AliExpressFreightService.UNAVAILABLE);
        verifyNoInteractions(payments, gateway);
    }

    @Test void existingPaymentAttemptCannotBypassFreightCheck() {
        Payment payment = mock(Payment.class);
        Order order = order();
        when(payment.getOrder()).thenReturn(order);
        when(payments.findByIdForUpdate(3L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> service.createCheckoutPreference(3L, null))
                .isInstanceOf(ResourceConflictException.class).hasMessage(AliExpressFreightService.UNAVAILABLE);
        verifyNoInteractions(gateway);
        verify(payment, never()).getProviderPreferenceId();
    }

    @Test void localOrdersDoNotRequireAliExpressFreight() {
        Order order = mock(Order.class);
        OrderItem item = mock(OrderItem.class);
        when(item.getSupplier()).thenReturn(null);
        when(order.getItems()).thenReturn(List.of(item));
        assertThatCode(() -> AliExpressFreightService.requireConfirmedFreight(order)).doesNotThrowAnyException();
        // An AliExpress sourcing reference does not turn local inventory into supplier fulfillment.
        when(item.getSupplier()).thenReturn("ALIEXPRESS");
        when(item.getFulfillmentType()).thenReturn(com.garage.garageapi.product.entity.FulfillmentType.LOCAL_STOCK);
        assertThatCode(() -> AliExpressFreightService.requireConfirmedFreight(order)).doesNotThrowAnyException();
    }
}

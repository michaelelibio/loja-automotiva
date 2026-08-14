package com.garage.garageapi.order.service;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderLifecycleServiceTests {
    private static final Instant TRANSITION_TIME = Instant.parse("2026-08-14T18:00:00Z");

    @Test
    void advancesPaidOrderThroughProcessingShippingAndDelivery() {
        Order order = order();
        order.markPaid();
        OrderLifecycleService service = service(order);

        service.transition(1L, OrderStatus.PROCESSING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getProcessingAt()).isEqualTo(TRANSITION_TIME);

        service.transition(1L, OrderStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getShippedAt()).isEqualTo(TRANSITION_TIME);

        service.transition(1L, OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(TRANSITION_TIME);
    }

    @Test
    void pendingOrderCannotStartProcessing() {
        assertConflict(() -> service(order()).transition(1L, OrderStatus.PROCESSING));
    }

    @Test
    void paidOrderCannotSkipDirectlyToShipped() {
        Order order = order();
        order.markPaid();
        assertConflict(() -> service(order).transition(1L, OrderStatus.SHIPPED));
    }

    @Test
    void deliveredOrderRejectsEveryFurtherManualTransition() {
        Order order = order();
        order.markPaid();
        OrderLifecycleService service = service(order);
        service.transition(1L, OrderStatus.PROCESSING);
        service.transition(1L, OrderStatus.SHIPPED);
        service.transition(1L, OrderStatus.DELIVERED);

        for (OrderStatus target : OrderStatus.values()) {
            assertConflict(() -> service.transition(1L, target));
        }
    }

    @Test
    void paymentFlowCanMarkPendingOrderPaidButManualServiceCannot() {
        Order paymentOrder = order();
        paymentOrder.markPaid();
        assertThat(paymentOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Order manualOrder = order();
        assertThatThrownBy(() -> service(manualOrder).transition(1L, OrderStatus.PAID))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("exclusivamente pelo fluxo de pagamento");
    }

    @Test
    void onlyPendingOrdersCanBeCanceledOrExpiredAndExpirationIsNotManual() {
        Order canceled = order();
        canceled.cancel();
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);

        Order expired = order();
        expired.expire();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        Order paid = order();
        paid.markPaid();
        assertConflict(paid::cancel);
        assertConflict(paid::expire);
        assertConflict(() -> service(order()).transition(1L, OrderStatus.EXPIRED));
    }

    private OrderLifecycleService service(Order order) {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findByIdForLifecycleUpdate(1L)).thenReturn(Optional.of(order));
        return new OrderLifecycleService(repository,
                Clock.fixed(TRANSITION_TIME, ZoneOffset.UTC));
    }

    private Order order() {
        User user = User.local("UsuÃ¡rio", "order@example.com", "encoded-password");
        Address address = new Address(user, "Casa", "Michael", "89229040", "Rua",
                "10", null, "Centro", "Joinville", "SC", true);
        return new Order(user, address, new BigDecimal("50.00"), BigDecimal.ZERO,
                Duration.ofHours(24));
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(ResourceConflictException.class);
    }
}

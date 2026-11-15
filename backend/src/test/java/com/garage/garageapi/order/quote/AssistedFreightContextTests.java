package com.garage.garageapi.order.quote;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistedFreightContextTests {
    OrderRepository orders=mock(OrderRepository.class);
    PaymentRepository payments=mock(PaymentRepository.class);
    OrderFulfillmentRepository fulfillments=mock(OrderFulfillmentRepository.class);
    AssistedQuoteService service=new AssistedQuoteService(orders,payments,fulfillments);
    Order order=mock(Order.class);
    void fixture() {
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(order.getAssistedQuote()).thenReturn(AssistedQuote.waiting());
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
        when(order.getExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
        when(order.getState()).thenReturn("SC");when(order.getSubtotal()).thenReturn(new BigDecimal("50"));
        var item=mock(OrderItem.class);when(item.getSupplier()).thenReturn("ALIEXPRESS");
        when(item.getSupplierProductId()).thenReturn("123");when(item.getSupplierVariantId()).thenReturn("456");
        when(item.getQuantity()).thenReturn(2);when(order.getItems()).thenReturn(List.of(item));
    }
    @Test void contextUsesPersistedOrderSkuQuantityAndStateWithoutMutatingQuote() {
        fixture();var context=service.freightContext(7L);
        assertThat(context.orderId()).isEqualTo(7L);assertThat(context.state()).isEqualTo("SC");
        assertThat(context.items()).singleElement().satisfies(i->{assertThat(i.skuId()).isEqualTo("456");assertThat(i.quantity()).isEqualTo(2);});
        verify(order,never()).updateAssistedQuote(any());verify(orders,never()).save(any());
        verifyNoInteractions(payments,fulfillments);
    }
    @Test void expiredOrderIsNotQueriedAgain() {
        fixture();when(order.getExpiresAt()).thenReturn(Instant.EPOCH);
        assertThatThrownBy(()->service.freightContext(7L)).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(payments,fulfillments);
    }
    @Test void paidOrderIsNotRequoted() {
        fixture();when(order.getStatus()).thenReturn(OrderStatus.PAID);
        assertThatThrownBy(()->service.freightContext(7L)).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(payments,fulfillments);
    }
}

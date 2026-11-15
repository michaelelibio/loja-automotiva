package com.garage.garageapi.shipping.commercial;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.service.PaymentAttemptService;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CommercialPaymentRevalidationTests {
    @Test void bothFuturePaymentEntryPointsRevalidateBeforeProviderOrNewPaymentRow(){
        var payments=mock(PaymentRepository.class);var orders=mock(OrderRepository.class);var gateway=mock(CheckoutProGateway.class);
        var commercial=mock(CommercialShippingService.class);var order=mock(Order.class);
        when(order.getCommercialShipping()).thenReturn(mock(CommercialShippingSnapshot.class));when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);when(order.getExpiresAt()).thenReturn(Instant.now().plusSeconds(60));
        when(orders.findByIdAndUserIdForUpdate(7L,8L)).thenReturn(Optional.of(order));
        doThrow(new ResourceConflictException("Synthetic cost change")).when(commercial).revalidate(order);
        // A local unit object only, with a mocked gateway; the application's payment flag remains false.
        var attempts=new PaymentAttemptService(payments,orders,gateway,true);ReflectionTestUtils.setField(attempts,"commercial",commercial);
        assertThatThrownBy(()->attempts.prepare(8L,7L)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(payments,gateway);
        var payment=mock(Payment.class);when(payment.getOrder()).thenReturn(order);when(payments.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(()->attempts.createCheckoutPreference(9L,null)).isInstanceOf(ResourceConflictException.class);
        verify(commercial,times(2)).revalidate(order);verify(payments,never()).saveAndFlush(any());verifyNoInteractions(gateway);
    }
}

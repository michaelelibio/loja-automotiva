package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.service.PaymentAttemptService;
import com.garage.garageapi.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanCheckoutSafetyTests {
    @Test void evenEnabledPaymentCannotChargeLegacyFreightOrderInControlledProfile() {
        var orders=mock(OrderRepository.class);
        var payments=mock(PaymentRepository.class);
        var gateway=mock(CheckoutProGateway.class);
        var user=User.local("Cliente Teste","safety@example.test","synthetic-hash");
        var address=new Address(user,"Casa","Cliente Teste","89229040","Rua Teste","10",null,"Centro","Joinville","SC",true);
        var order=new Order(user,address,BigDecimal.TEN,BigDecimal.ONE,Duration.ofHours(24));
        when(orders.findByIdAndUserIdForUpdate(1L,2L)).thenReturn(Optional.of(order));
        var service=new PaymentAttemptService(payments,orders,gateway,true);
        var environment=new MockEnvironment(); environment.setActiveProfiles("controlled","local-freight");
        ReflectionTestUtils.setField(service,"environment",environment);
        assertThatThrownBy(() -> service.prepare(2L,1L)).hasMessageContaining("entrega grátis comprovada");
        verifyNoInteractions(payments,gateway);
    }
}

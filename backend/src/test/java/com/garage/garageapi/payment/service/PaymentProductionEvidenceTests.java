package com.garage.garageapi.payment.service;

import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.entity.*;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.order.email.OrderEmailNotificationService;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PaymentProductionEvidenceTests {
    @Test void providerLookupPersistsTrueFalseAndUnknownWithoutPromotingHistoricalPayments() {
        for(Boolean live:new Boolean[]{true,false,null}) {
            var repository=mock(PaymentRepository.class);
            var order=order();var payment=new Payment(order,PaymentMethod.MERCADO_PAGO);
            ReflectionTestUtils.setField(payment,"id",2L);
            when(repository.findByExternalReferenceForUpdate("garage_order_1_payment_2")).thenReturn(Optional.of(payment));
            var updater=new CheckoutProWebhookUpdater(repository,mock(OrderEmailNotificationService.class),mock(OrderFulfillmentNotificationService.class));
            var response=new CheckoutProGateway.PaymentResult("remote-id","garage_order_1_payment_2",PaymentStatus.PAID,"approved",
                    BigDecimal.TEN,"BRL","pix","pix",Instant.now(),live);
            updater.apply("remote-id",response);updater.apply("remote-id",response);
            assertThat(payment.getProviderLiveMode()).isEqualTo(live);
            assertThat(payment.getProviderVerifiedAt()).isNotNull();assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }
        var legacy=new Payment(order(),PaymentMethod.MERCADO_PAGO);
        legacy.synchronizeCheckoutPayment("legacy-id",PaymentStatus.PAID,Instant.now(),"pix","pix");
        assertThat(legacy.getProviderLiveMode()).isNull();assertThat(legacy.getProviderVerifiedAt()).isNull();
    }
    @Test void wrongAmountCannotEstablishProductionEvidence() {
        var repository=mock(PaymentRepository.class);var payment=new Payment(order(),PaymentMethod.MERCADO_PAGO);
        ReflectionTestUtils.setField(payment,"id",2L);
        when(repository.findByExternalReferenceForUpdate("garage_order_1_payment_2")).thenReturn(Optional.of(payment));
        var updater=new CheckoutProWebhookUpdater(repository,mock(OrderEmailNotificationService.class),mock(OrderFulfillmentNotificationService.class));
        updater.apply("remote-id",new CheckoutProGateway.PaymentResult("remote-id","garage_order_1_payment_2",PaymentStatus.PAID,"approved",
                BigDecimal.ONE,"BRL","pix","pix",Instant.now(),true));
        assertThat(payment.getProviderLiveMode()).isNull();assertThat(payment.getProviderVerifiedAt()).isNull();
    }
    private Order order() {
        var user=User.local("Cliente Teste","production-evidence@example.test","synthetic-hash");
        var address=new Address(user,"Casa","Cliente Teste","89229040","Rua Teste","10",null,"Centro","Joinville","SC",true);
        var order=new Order(user,address,BigDecimal.TEN,BigDecimal.ZERO,Duration.ofHours(1));ReflectionTestUtils.setField(order,"id",1L);return order;
    }
}

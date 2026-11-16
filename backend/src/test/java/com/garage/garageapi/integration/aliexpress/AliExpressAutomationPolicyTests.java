package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressAutomationPolicyTests {
    private static final String FROM="2026-11-15T22:00:00Z";

    @Test void explicitActivationAndFixedCutoffAreBothRequired() {
        assertThat(new AliExpressAutomationPolicy(false,FROM).enabled()).isFalse();
        var missing=new AliExpressAutomationPolicy(true,"");
        assertThat(missing.enabled()).isFalse();
        assertThat(missing.blockedReason(mock(Order.class))).contains("data inicial");
        assertThatThrownBy(()->new AliExpressAutomationPolicy(true,"invalid"))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test void historicalAndUndatedOrdersRemainExcludedAcrossRestarts() {
        var order=mock(Order.class);var policy=new AliExpressAutomationPolicy(true,FROM);
        assertThat(policy.blockedReason(order)).contains("anterior");
        when(order.getCreatedAt()).thenReturn(Instant.parse(FROM).minusNanos(1));
        assertThat(policy.blockedReason(order)).contains("Reconcilie");
        assertThat(new AliExpressAutomationPolicy(true,FROM).blockedReason(order)).contains("Reconcilie");
        when(order.getCreatedAt()).thenReturn(Instant.parse(FROM));
        assertThat(policy.blockedReason(order)).isNull();
        when(order.getCreatedAt()).thenReturn(Instant.parse(FROM).plusSeconds(1));
        assertThat(policy.blockedReason(order)).isNull();
    }

    @Test void disabledOrUnconfiguredWorkerDoesNotScanOrScheduleAnything() {
        var repository=mock(OrderFulfillmentRepository.class);var service=mock(AliExpressOrderService.class);
        var executor=mock(org.springframework.core.task.TaskExecutor.class);
        for(var policy:new AliExpressAutomationPolicy[]{new AliExpressAutomationPolicy(false,FROM),new AliExpressAutomationPolicy(true,"")}) {
            var worker=new AliExpressDispatchWorker(repository,service,executor,policy);
            worker.enqueue(21L);worker.recoverPending();
        }
        verifyNoInteractions(repository,service,executor);
    }

    @Test void directAutomaticEntryPointCannotBypassTheDisabledFlag() {
        var orders=mock(com.garage.garageapi.order.repository.OrderRepository.class);
        var payments=mock(com.garage.garageapi.payment.repository.PaymentRepository.class);
        var client=mock(AliExpressOrderClient.class);var tokens=mock(AliExpressTokenService.class);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        for(var policy:new AliExpressAutomationPolicy[]{new AliExpressAutomationPolicy(false,FROM),new AliExpressAutomationPolicy(true,"")}) {
            var service=new AliExpressOrderService(orders,payments,null,null,client,tokens,manager,null,null,null,policy);
            service.sendAutomatically(21L);
        }
        verifyNoInteractions(orders,payments,client,tokens,manager);
    }
}

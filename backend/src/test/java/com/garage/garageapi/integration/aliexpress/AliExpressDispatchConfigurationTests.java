package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressDispatchConfigurationTests {
    private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(AliExpressDispatchConfiguration.class)
            .withBean(OrderFulfillmentRepository.class,()->mock(OrderFulfillmentRepository.class))
            .withBean(AliExpressOrderService.class,()->mock(AliExpressOrderService.class));
    @Test void automationIsOffByDefaultAndOptInCreatesBoundedWorker() {
        context.run(c->assertThat(c).doesNotHaveBean(AliExpressDispatchWorker.class));
        context.withPropertyValues("aliexpress.orders.auto-dispatch-enabled=true","aliexpress.orders.dispatch-poll-ms=3600000")
                .run(c->assertThat(c).hasSingleBean(AliExpressDispatchWorker.class));
    }
    @Test void appPermissionEvidenceCannotBeReusedForAnotherApplication() {
        var permission=new AliExpressPlaceOrderPermission();
        assertThat(permission.verified()).isFalse();
        ReflectionTestUtils.setField(permission,"appKey","synthetic-app");
        ReflectionTestUtils.setField(permission,"appHash",AliExpressOAuthStateService.hash("synthetic-app"));
        ReflectionTestUtils.setField(permission,"evidence","approved-provider-method-evidence");
        assertThat(permission.verified()).isTrue();
        ReflectionTestUtils.setField(permission,"appKey","different-app");assertThat(permission.verified()).isFalse();
    }
}

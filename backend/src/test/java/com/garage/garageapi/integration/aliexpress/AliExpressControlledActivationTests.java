package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.core.io.support.ResourcePropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AliExpressControlledActivationTests {
    @Test void springApplicationJsonOverridesTrueAliasesAndKeepsThePauseEffective() throws Exception {
        var env=environment(Map.of("ALIEXPRESS_AUTO_DISPATCH_ENABLED","true","ALIEXPRESS_ORDERS_ENABLED","true"));
        env.getPropertySources().addFirst(new MapPropertySource("json-input",Map.of("spring.application.json",
                "{\"aliexpress\":{\"orders\":{\"auto-dispatch-enabled\":false,\"enabled\":false}}}")));
        new org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor()
                .postProcessEnvironment(env,new org.springframework.boot.SpringApplication());
        assertThat(env.getProperty("aliexpress.orders.auto-dispatch-enabled",Boolean.class)).isFalse();
        assertThat(env.getProperty("aliexpress.orders.enabled",Boolean.class)).isFalse();
    }
    @Test void controlledCreationAndQueryRemainOffWithoutExplicitActivation() throws Exception {
        var env=environment(Map.of());
        for(String key:new String[]{"enabled","contract-confirmed","sync-enabled","sync-contract-confirmed","auto-dispatch-enabled"})
            assertThat(env.getProperty("aliexpress.orders."+key,Boolean.class)).isFalse();
    }
    @Test void productionFlagsActivateCreationAndQueryIndependentlyOfPaymentConfiguration() throws Exception {
        var env=environment(Map.of("ALIEXPRESS_ORDERS_ENABLED","true","ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED","true",
                "ALIEXPRESS_ORDER_SYNC_ENABLED","true","ALIEXPRESS_ORDER_SYNC_CONTRACT_CONFIRMED","true","ALIEXPRESS_AUTO_DISPATCH_ENABLED","true"));
        for(String key:new String[]{"enabled","contract-confirmed","sync-enabled","sync-contract-confirmed","auto-dispatch-enabled"})
            assertThat(env.getProperty("aliexpress.orders."+key,Boolean.class)).isTrue();
        assertThat(env.getProperty("app.payment.creation-enabled",Boolean.class)).isFalse();
    }
    @Test void creationActivationAloneDoesNotGrantSupplierQuery() throws Exception {
        var env=environment(Map.of("ALIEXPRESS_ORDERS_ENABLED","true","ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED","true"));
        assertThat(env.getProperty("aliexpress.orders.enabled",Boolean.class)).isTrue();
        assertThat(env.getProperty("aliexpress.orders.sync-enabled",Boolean.class)).isFalse();
    }
    private StandardEnvironment environment(Map<String,Object> values) throws Exception {
        var env=new StandardEnvironment();env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("production",values));
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application-controlled.properties"));
        // Test resources intentionally replace application.properties; inspect the actual release defaults here.
        env.getPropertySources().addLast(new ResourcePropertySource("file:src/main/resources/application.properties"));return env;
    }
}

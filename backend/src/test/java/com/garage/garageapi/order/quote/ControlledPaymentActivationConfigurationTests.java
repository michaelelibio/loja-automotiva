package com.garage.garageapi.order.quote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ControlledPaymentActivationConfigurationTests {
    @Test
    void controlledRemainsDisabledWithoutAnExplicitEnvironmentOverride() throws Exception {
        assertPaymentFlag(Map.of(), false);
    }

    @Test
    void legacyPlaceholderAloneCannotOverrideTheControlledProfile() throws Exception {
        assertPaymentFlag(Map.of("PAYMENT_CREATION_ENABLED", "true"), false);
    }

    @Test
    void canonicalEnvironmentFlagEnablesOnlyPaymentWithoutChangingTheControlledProfile() throws Exception {
        assertPaymentFlag(Map.of("APP_PAYMENT_CREATION_ENABLED", "true",
                "PAYMENT_CREATION_ENABLED", "true"), true);
    }

    @Test
    void canonicalEnvironmentFlagTakesPrecedenceOverTheLegacyPlaceholder() throws Exception {
        assertPaymentFlag(Map.of("APP_PAYMENT_CREATION_ENABLED", "false",
                "PAYMENT_CREATION_ENABLED", "true"), false);
        assertPaymentFlag(Map.of("APP_PAYMENT_CREATION_ENABLED", "true",
                "PAYMENT_CREATION_ENABLED", "false"), true);
    }

    private void assertPaymentFlag(Map<String, Object> variables, boolean expected) throws Exception {
        var environment = new StandardEnvironment();
        // Isolate these checks from the developer machine's environment and JVM flags.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("railway", variables));
        environment.getPropertySources().addLast(
                new ResourcePropertySource("classpath:application-controlled.properties"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(PaymentFlag.class);
            context.refresh();
            // Exercise the same @Value lookup used by PaymentAttemptService.
            assertThat(context.getBean(PaymentFlag.class).enabled).isEqualTo(expected);
            for (String property : new String[]{"aliexpress.orders.enabled",
                    "aliexpress.orders.contract-confirmed", "aliexpress.orders.sync-enabled",
                    "aliexpress.orders.sync-contract-confirmed", "dsers.simulation.enabled",
                    "app.seed.products.enabled"}) {
                assertThat(environment.getProperty(property, Boolean.class)).as(property).isFalse();
            }
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        }
    }

    static class PaymentFlag {
        final boolean enabled;

        PaymentFlag(@Value("${app.payment.creation-enabled:false}") boolean enabled) {
            this.enabled = enabled;
        }
    }
}

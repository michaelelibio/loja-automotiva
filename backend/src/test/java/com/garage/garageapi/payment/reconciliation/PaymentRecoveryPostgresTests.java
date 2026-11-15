package com.garage.garageapi.payment.reconciliation;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named="PAYMENT_RECOVERY_POSTGRES_TESTS",matches="true")
class PaymentRecoveryPostgresTests extends PaymentRecoveryIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:55449/payment_recovery");
        p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.datasource.username",()->"checkout_test");p.add("spring.datasource.password",()->"");
    }
}

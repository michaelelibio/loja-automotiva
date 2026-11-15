package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs the same payment, split-order, timeout and sync concurrency suite in a disposable DB. */
@EnabledIfEnvironmentVariable(named="CLEAN_CHECKOUT_POSTGRES_TESTS",matches="true")
class AliExpressOperationPostgresTests extends AliExpressOrderIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:55449/clean_operation");
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->"checkout_test");
        properties.add("spring.datasource.password",()->"");
    }
}

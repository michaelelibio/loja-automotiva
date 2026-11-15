package com.garage.garageapi.integration.aliexpress;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Same real transaction/locking suite against a disposable loopback PostgreSQL, never a production URL. */
@EnabledIfEnvironmentVariable(named="ADMIN_ORDER19_POSTGRES_TESTS",matches="true")
class AdminAliExpressOrder19PostgresTests extends AdminAliExpressTestIntegrationTests {
    @DynamicPropertySource static void localDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:55450/admin_order19_integration");
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->"order19_test");
        properties.add("spring.datasource.password",()->"");
    }
}

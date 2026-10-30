package com.garage.garageapi.order.quote;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HomologationConfigurationTests {
    @Test void controlledProfileDisablesSchemaWritesPaymentsAndSupplierOrders() throws Exception {
        Properties profile=new Properties();
        try(var stream=new ClassPathResource("application-controlled.properties").getInputStream()) {profile.load(stream);}
        assertThat(profile.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(profile.getProperty("spring.sql.init.mode")).isEqualTo("never");
        for(String flag:List.of("app.payment.creation-enabled","app.seed.products.enabled",
                "aliexpress.orders.enabled","aliexpress.orders.contract-confirmed","dsers.simulation.enabled"))
            assertThat(profile.getProperty(flag)).as(flag).isEqualTo("false");
    }
    @Test void sandboxProfileNeverInheritsProductionDatabaseOrPaymentCredential() throws Exception {
        Properties profile=new Properties();
        try(var stream=new ClassPathResource("application-homologation.properties").getInputStream()) {profile.load(stream);}
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-fixture",Map.of(
            "DB_URL","jdbc:postgresql://production.invalid/prod","MERCADO_PAGO_ACCESS_TOKEN","synthetic-production-token",
            "HML_POSTGRES_PORT","55439","HML_MERCADO_PAGO_ACCESS_TOKEN","synthetic-test-token")));
        assertThat(environment.resolveRequiredPlaceholders(profile.getProperty("spring.datasource.url")))
            .isEqualTo("jdbc:postgresql://127.0.0.1:55439/ingarage_homologation");
        assertThat(environment.resolveRequiredPlaceholders(profile.getProperty("mercadopago.access-token"))).isEqualTo("synthetic-test-token");
        assertThat(profile.getProperty("spring.datasource.username")).isEqualTo("ingarage_hml");
        assertThat(profile.getProperty("mercadopago.checkout-pro.sandbox")).isEqualTo("true");
        assertThat(profile.getProperty("aliexpress.orders.enabled")).isEqualTo("false");
        assertThat(profile.getProperty("aliexpress.orders.contract-confirmed")).isEqualTo("false");
        assertThat(profile.getProperty("mercadopago.diagnostic-logging")).isEqualTo("false");
    }
}

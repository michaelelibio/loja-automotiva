package com.garage.garageapi.order.quote;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

/** Explicit opt-in. No URL from DB_URL/.env is accepted; only the dedicated local database. */
@EnabledIfEnvironmentVariable(named="INGARAGE_HML_POSTGRES",matches="true")
@SpringBootTest(properties={"aliexpress.orders.enabled=false","aliexpress.orders.contract-confirmed=false",
    "app.account.email.mode=disabled","spring.jpa.hibernate.ddl-auto=create",
    "spring.sql.init.mode=never","mercadopago.webhook-secret=homologation-synthetic-secret",
    "mercadopago.diagnostic-logging=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AssistedQuotePostgresHomologationTests extends AssistedQuoteIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url=System.getenv("INGARAGE_HML_PG_URL");
        if(url==null || !url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/ingarage_homologation"))
            throw new IllegalStateException("A dedicated loopback homologation database is required.");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->"ingarage_hml");
        properties.add("spring.datasource.password",()->"");
    }
    @BeforeEach void verifyIsolation() {
        assertThat(jdbc.<String>queryForObject("select current_database()",String.class)).isEqualTo("ingarage_homologation");
        assertThat(jdbc.<String>queryForObject("select host(inet_server_addr())",String.class)).isEqualTo("127.0.0.1");
    }
    @Test void additiveMigrationsApplyTwiceAndPreserveLegacyRowsAndConstraints() throws Exception {
        var legacy=new Order(user,address,new BigDecimal("50.00"),new BigDecimal("24.00"),Duration.ofHours(24));
        orders.saveAndFlush(legacy);
        jdbc.execute("create schema migration_audit");
        jdbc.execute("create table migration_audit.orders (like public.orders including all)");
        jdbc.execute("insert into migration_audit.orders select * from public.orders");
        jdbc.execute("create table migration_audit.order_items (like public.order_items including all)");
        // Reconstruct the schema immediately before the additive order migrations, in an isolated schema.
        List<String> scripts=List.of("V20260820_01__order_item_supplier_snapshot.sql",
            "V20260820_02__order_shipping_provider_snapshot.sql","V20260820_03__order_fulfillment.sql",
            "V20261015_01__aliexpress_manual_orders.sql","V20261030_01__assisted_quote.sql");
        for(String file:scripts) {
            String sql=new String(new ClassPathResource("db/migration/"+file).getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var additions=java.util.regex.Pattern.compile("(?i)ALTER TABLE\\s+(orders|order_items)\\s+ADD COLUMN IF NOT EXISTS\\s+([a-z_]+)").matcher(sql);
            while(additions.find()) jdbc.execute("alter table migration_audit."+additions.group(1)+" drop column if exists "+additions.group(2)+" cascade");
        }
        assertThat(jdbc.<Integer>queryForObject("select count(*) from information_schema.columns where table_schema='migration_audit' and table_name='orders' and column_name='assisted_quote'",Integer.class)).isZero();
        jdbc.execute("set local search_path=migration_audit,pg_catalog");
        for(int pass=0;pass<2;pass++) for(String file:scripts)
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/"+file));return null;
            });
        assertThat(jdbc.<BigDecimal>queryForObject("select total from orders where id=?",BigDecimal.class,legacy.getId())).isEqualByComparingTo("74.00");
        assertThat(jdbc.<String>queryForObject("select status from orders where id=?",String.class,legacy.getId())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.<Integer>queryForObject("select count(*) from orders where assisted_quote is not null or recipient_cpf is not null",Integer.class)).isZero();
        assertThat(jdbc.<String>queryForObject("select data_type from information_schema.columns where table_schema='migration_audit' and table_name='orders' and column_name='assisted_quote'",String.class)).isEqualTo("jsonb");
        jdbc.update("update orders set assisted_quote=?::jsonb, request_key=? where id=?","{\"state\":\"CONFIRMED\",\"amount\":74.00}",UUID.randomUUID().toString(),legacy.getId());
        assertThat(jdbc.<String>queryForObject("select assisted_quote->>'state' from orders where id=?",String.class,legacy.getId())).isEqualTo("CONFIRMED");
        jdbc.update("insert into order_fulfillments(order_id,status,provider,external_reference,attempt_count,created_at,updated_at) values (?,'VERIFICATION_REQUIRED','ALIEXPRESS',?,0,now(),now())",legacy.getId(),"HML-"+legacy.getId());
        Long fulfillment=jdbc.<Long>queryForObject("select id from order_fulfillments where order_id=?",Long.class,legacy.getId());
        jdbc.update("insert into manual_supplier_order_refs values (?,?)",fulfillment,"900000000000001");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            Savepoint point=connection.setSavepoint();
            try(var statement=connection.prepareStatement("insert into manual_supplier_order_refs values (?,?)")) {
                statement.setLong(1,fulfillment);statement.setString(2,"900000000000001");
                assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23505"));
            } finally {connection.rollback(point);}
            return null;
        });
        jdbc.update("delete from orders where id=?",legacy.getId());
        assertThat(jdbc.<Integer>queryForObject("select count(*) from manual_supplier_order_refs",Integer.class)).isZero();
        jdbc.execute("set local search_path=public,pg_catalog");
    }
    @Test void signedWebhookIsIdempotentAndNeverCallsAliExpress() throws Exception {
        long id=create();confirm(id);accept(id);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isCreated());
        var payment=payments.findAllByOrderIdOrderByCreatedAtDescIdDesc(id).get(0);
        var result=new com.garage.garageapi.payment.gateway.CheckoutProGateway.PaymentResult("900123",payment.getExternalReference(),
            com.garage.garageapi.payment.entity.PaymentStatus.PAID,"approved",new BigDecimal("74.00"),"BRL","credit_card","master",Instant.now());
        when(gateway.findPayment("900123")).thenReturn(result);
        String requestId="homologation-request",timestamp=Long.toString(Instant.now().getEpochSecond());
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("homologation-synthetic-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String signature="ts="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal(("id:900123;request-id:"+requestId+";ts:"+timestamp+";").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(int i=0;i<3;i++) mvc.perform(post("/api/webhooks/mercadopago").queryParam("data.id","900123").queryParam("type","payment")
                .header("x-request-id",requestId).header("x-signature",signature)).andExpect(status().isOk());
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payments.findAllByOrderIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
        assertThat(fulfillments.findByOrderId(id).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
        recheck(id,60,"ACCEPTED");
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"address","price","modality","sku","quantity"})
    void changedQuoteSnapshotCannotReachPaymentGateway(String changed) throws Exception {
        long id=create();confirm(id);accept(id);
        var order=orders.findByIdWithItems(id).orElseThrow();
        switch(changed) {
            case "address" -> org.springframework.test.util.ReflectionTestUtils.setField(order,"street","Outro endereco");
            case "price" -> org.springframework.test.util.ReflectionTestUtils.setField(order,"total",new BigDecimal("75.00"));
            case "modality" -> {
                var leg=order.getShippingLegs().get(0);
                org.springframework.test.util.ReflectionTestUtils.setField(order,"shippingLegs",List.of(new ShippingProvider.Leg(
                    leg.provider(),"OTHER_CODE",leg.name(),leg.originCountry(),leg.amount(),leg.currency(),leg.priceBrl(),leg.estimatedDays(),leg.supplierVariantIds())));
            }
            case "sku" -> org.springframework.test.util.ReflectionTestUtils.setField(order.getItems().get(0),"supplierVariantId","999999");
            case "quantity" -> org.springframework.test.util.ReflectionTestUtils.setField(order.getItems().get(0),"quantity",2);
            default -> throw new AssertionError(changed);
        }
        orders.saveAndFlush(order);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isConflict());
        verifyNoInteractions(gateway);
    }
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Test void contextDigestSurvivesPostgresJsonbAndNumericRoundTrip() throws Exception {
        long id=create();confirm(id);entityManager.flush();entityManager.clear();accept(id);
        var order=orders.findById(id).orElseThrow();
        assertThat(order.getAssistedQuote().contextDigest()).hasSize(64);
        assertThatCode(order::requireQuoteContextUnchanged).doesNotThrowAnyException();
    }
}

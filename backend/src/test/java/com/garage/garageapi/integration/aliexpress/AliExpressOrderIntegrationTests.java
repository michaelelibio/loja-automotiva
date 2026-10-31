package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.fulfillment.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.*;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aliexpress.orders.enabled=true","aliexpress.orders.contract-confirmed=true",
        "aliexpress.orders.sync-enabled=true","aliexpress.orders.sync-contract-confirmed=true"})
@AutoConfigureMockMvc
class AliExpressOrderIntegrationTests {
    @Autowired AliExpressOrderService service;
    @Autowired AliExpressOrderPreparation preparation;
    @Autowired OrderRepository orders;
    @MockitoSpyBean OrderFulfillmentRepository fulfillments;
    @Autowired PaymentRepository payments;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductMediaRepository media;
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired MockMvc mvc;
    @MockitoSpyBean OrderFulfillmentInitializer initializer;
    @MockitoBean AliExpressPlaceOrderPermission permission;
    @MockitoBean AliExpressOrderClient client;
    @MockitoBean AliExpressOrderQueryClient queryClient;
    @MockitoBean AliExpressOrderQueryPermission queryPermission;
    @Autowired AliExpressOrderSyncService syncService;
    @MockitoBean AliExpressTokenService tokens;

    @BeforeEach void clean() {
        reset(client,tokens,initializer,fulfillments);
        reset(queryClient,queryPermission);
        when(queryPermission.verified()).thenReturn(true);
        when(permission.verified()).thenReturn(true);
        fulfillments.deleteAll();payments.deleteAll();orders.deleteAll();media.deleteAll();variants.deleteAll();products.deleteAll();addresses.deleteAll();users.deleteAll();
        when(tokens.accessToken()).thenReturn("synthetic-token-never-log");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("900000000000001","900000000000002"),false));
    }
    @Test void pendingOrUnconfirmedPaymentCannotCallSupplier() throws Exception {
        var order=fixture(false,true,"ALIEXPRESS");
        assertThat(service.view(order.getId()).paymentConfirmed()).isFalse();
        mvc.perform(post(path(order)).with(admin())).andExpect(status().isConflict());
        order.markPaid();orders.saveAndFlush(order);
        mvc.perform(post(path(order)).with(admin())).andExpect(status().isConflict());
        verifyNoInteractions(client,tokens);
    }
    @Test void adminRequiredForReadAndSend() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        mvc.perform(get(path(order).replace("/send",""))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(order))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(order)).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        verifyNoInteractions(client,tokens);
    }
    @Test void successStoresAllExternalIdsAndManualPaymentWithoutShippingAndIsIdempotent() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        mvc.perform(post(path(order)).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.supplierStatus").value("AWAITING_MANUAL_PAYMENT"))
                .andExpect(jsonPath("$.supplierOrderIds.length()").value(2));
        var state=service.view(order.getId());assertThat(state.canSend()).isFalse();
        mvc.perform(post(path(order)).with(admin())).andExpect(status().isOk());
        verify(client,times(1)).create(argThat(payload -> payload.keySet().equals(Set.of("logistics_address","product_items"))),anyString());
        var saved=orders.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(saved.getProcessingAt()).isNotNull();assertThat(saved.getShippedAt()).isNull();assertThat(saved.getDeliveredAt()).isNull();
        assertThat(payments.findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(order.getId(),PaymentStatus.PAID)).isPresent();
        mvc.perform(get("/api/orders/"+order.getId()).with(jwt().jwt(j->j.subject(order.getUser().getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recipientCpf"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("52998224725"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-token"))));
        mvc.perform(get("/api/admin/orders/"+order.getId()).with(admin())).andExpect(jsonPath("$.status").value("PROCESSING"));
    }
    @Test void timeoutOrAmbiguousResultPersistsVerificationAndBlocksResend() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenThrow(new RuntimeException("synthetic-private-body"));
        var state=service.send(order.getId());
        assertThat(state.status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThat(state.lastError()).doesNotContain("synthetic-private-body");
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }
    @Test void definitiveRejectionAllowsOnlyExplicitManualRetryWithSameReference() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),true));
        assertThat(service.send(order.getId()).status()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(service.view(order.getId()).canSend()).isTrue();
        service.send(order.getId());
        verify(client,times(2)).create(argThat(payload -> payload.keySet().equals(Set.of("logistics_address","product_items"))),anyString());
    }
    @Test void concurrentRequestsCreateAtMostOneSupplierPurchase() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(client.create(anyMap(),anyString())).thenAnswer(inv->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return new AliExpressOrderClient.Result(List.of("90001"),false);});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->service.send(order.getId()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{try {service.send(order.getId());}catch(com.garage.garageapi.shared.exception.ResourceConflictException expected) { }});
            second.get(10,TimeUnit.SECONDS);release.countDown();first.get(10,TimeUnit.SECONDS);
            verify(client,times(1)).create(anyMap(),anyString());
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void missingCheckoutFieldsBlockBeforeCredentialsOrSupplier() {
        var order=fixture(true,false,"ALIEXPRESS");
        assertThat(service.view(order.getId()).blockReason()).contains("destinatário", "pendentes");
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @ParameterizedTest @ValueSource(strings={"recipientCpf","recipientBirthDate","recipientPhone","shippingLegs"})
    void missingBrRequirementsOrShippingBlockCreation(String field) {
        var order=fixture(true,true,"ALIEXPRESS");ReflectionTestUtils.setField(order,field,null);orders.saveAndFlush(order);
        assertThat(service.view(order.getId()).canSend()).isFalse();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void otherSuppliersAreNotModified() {
        var order=fixture(true,true,"CJ");
        var before=fulfillments.findByOrderId(order.getId()).orElseThrow();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        var after=fulfillments.findByOrderId(order.getId()).orElseThrow();
        assertThat(after.getProvider()).isEqualTo(before.getProvider());assertThat(after.getStatus()).isEqualTo(before.getStatus());
        verifyNoInteractions(client,tokens);
    }
    @Test void persistedMarkerFromInterruptedRequestIsNeverReclaimed() {
        var order=fixture(true,true,"ALIEXPRESS");
        var state=fulfillments.findByOrderId(order.getId()).orElseThrow();state.beginAliExpress(Instant.now().minusSeconds(3600));fulfillments.saveAndFlush(state);
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @ParameterizedTest @ValueSource(strings={"cpf","birthDate","phone","address","quantity","sku","rawSku","shipping"})
    void invalidRecipientItemsAndShippingCannotBeSent(String field) {
        var order=fixture(true,true,"ALIEXPRESS");
        switch(field) {
            case "cpf" -> ReflectionTestUtils.setField(order,"recipientCpf","11111111111");
            case "birthDate" -> ReflectionTestUtils.setField(order,"recipientBirthDate",LocalDate.now().plusDays(1));
            case "phone" -> ReflectionTestUtils.setField(order,"recipientPhone","123");
            case "address" -> ReflectionTestUtils.setField(order,"state","XX");
            case "quantity" -> ReflectionTestUtils.setField(order.getItems().get(0),"quantity",0);
            case "sku" -> ReflectionTestUtils.setField(order.getItems().get(0),"supplierVariantId","999999");
            case "rawSku" -> {var variant=variants.findAll().get(0);ReflectionTestUtils.setField(variant,"rawVariantKey",null);variants.saveAndFlush(variant);}
            case "shipping" -> ReflectionTestUtils.setField(order,"shippingLegs",List.of(new ShippingProvider.Leg("ALIEXPRESS","BAD","BAD","CN",BigDecimal.ONE,"BRL",BigDecimal.ONE,15,List.of("wrong-sku"))));
            default -> throw new AssertionError();
        }
        orders.saveAndFlush(order);
        assertThat(service.view(order.getId()).canSend()).isFalse();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void automaticFulfillmentDispatcherNeverCreatesAliExpressOrders() {
        var order=fixture(true,true,"ALIEXPRESS");
        dispatcher.fulfill(order.getId());
        verifyNoInteractions(client,tokens);
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.PENDING);
    }
    @Autowired SupplierFulfillmentService dispatcher;
    @Test void localFailureAfterRemoteSuccessKeepsDurableUnknownState() {
        var order=fixture(true,true,"ALIEXPRESS");
        doNothing().doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-storage-failure"))
                .when(fulfillments).flush();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void additiveMigrationCanRunTwiceWithoutChangingHistoricalSupplierState() throws Exception {
        var order=fixture(true,true,"CJ");
        try(var connection=dataSource.getConnection()) {
            var resource=new org.springframework.core.io.ClassPathResource("db/migration/V20261015_01__aliexpress_manual_orders.sql");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,resource);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,resource);
        }
        var fulfillment=fulfillments.findByOrderId(order.getId()).orElseThrow();
        assertThat(fulfillment.getProvider()).isEqualTo("CJ");assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PENDING);
    }
    @Autowired javax.sql.DataSource dataSource;
    @Test void defaultDisabledContractCannotBeBypassedByCompleteData() {
        var order=fixture(true,true,"ALIEXPRESS");
        // Entity relationships needed by validation are loaded within this transaction in normal service calls.
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(s->{
            var loaded=orders.findByIdWithItems(order.getId()).orElseThrow();
            assertThat(new AliExpressOrderPreparation(variants,false,false).blockedReason(loaded)).contains("contrato");
        });
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @ParameterizedTest @ValueSource(strings={"sandbox","unknown","unverified"})
    void nonProductionPaymentNeverTouchesSupplierOrCredentials(String kind) {
        var order=fixture(true,true,"ALIEXPRESS");
        var payment=payments.findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(order.getId(),PaymentStatus.PAID).orElseThrow();
        payment.recordProviderEnvironment("sandbox".equals(kind) ? Boolean.FALSE : "unknown".equals(kind) ? null : Boolean.TRUE,
                "unverified".equals(kind) ? null : Instant.now());
        payments.saveAndFlush(payment);
        assertThat(service.view(order.getId()).paymentConfirmed()).isFalse();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(tokens,client,queryClient);
    }
    @Test void splitOrdersSynchronizeIndividuallyWithoutPayingOrRecreatingOrders() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");service.send(order.getId());
        when(queryClient.query(eq("900000000000001"),anyString())).thenReturn(AliExpressSupplierSnapshot.classify("900000000000001","PLACE_ORDER_SUCCESS",null));
        when(queryClient.query(eq("900000000000002"),anyString())).thenReturn(AliExpressSupplierSnapshot.classify("900000000000002","WAIT_SELLER_SEND_GOODS",null));
        var state=syncService.sync(order.getId());
        assertThat(state.supplierStatus()).isEqualTo("MIXED");
        assertThat(state.supplierSync().parts()).extracting(AliExpressSupplierSnapshot.Part::state)
                .containsExactly("AWAITING_MANUAL_PAYMENT","AWAITING_SHIPMENT");
        assertThat(state.supplierSync().parts()).allMatch(AliExpressSupplierSnapshot.Part::verified);
        assertThat(state.supplierOrderIds()).containsExactly("900000000000001","900000000000002");
        mvc.perform(get("/api/admin/orders/"+order.getId()+"/fulfillment").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplierOrderIds.length()").value(2));
        service.send(order.getId());verify(client,times(1)).create(anyMap(),anyString());
        assertThat(fulfillments.findByOrderId(order.getId()).orElseThrow().getAttemptCount()).isEqualTo(1);
    }
    @Test void failedOrUnknownLookupNeverClaimsSupplierPaymentAndDoesNotUnlockResend() {
        var order=fixture(true,true,"ALIEXPRESS");service.send(order.getId());
        when(queryClient.query(anyString(),anyString())).thenThrow(new RuntimeException("synthetic-private-token"));
        var state=syncService.sync(order.getId());
        assertThat(state.supplierSync().parts()).allMatch(p->!p.verified() && "UNKNOWN".equals(p.state()));
        assertThat(state.supplierSync().toString()).doesNotContain("synthetic-private-token");
        assertThat(state.status()).isEqualTo(FulfillmentStatus.CREATED);
        service.send(order.getId());verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void syncPermissionAndAdminAuthorizationAreIndependentFromCreation() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        mvc.perform(post("/api/admin/orders/"+order.getId()+"/aliexpress/sync")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/orders/"+order.getId()+"/aliexpress/sync").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        when(queryPermission.verified()).thenReturn(false);
        assertThatThrownBy(()->syncService.sync(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens,queryClient);
    }
    @Test void concurrentSyncUsesOneClaimAndNeverCreatesAnotherSupplierOrder() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");service.send(order.getId());
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(queryClient.query(anyString(),anyString())).thenAnswer(inv->{entered.countDown();release.await(10,TimeUnit.SECONDS);
            return AliExpressSupplierSnapshot.classify(inv.getArgument(0),"RISK_CONTROL",null);});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->syncService.sync(order.getId()));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->syncService.sync(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
            release.countDown();first.get(10,TimeUnit.SECONDS);
            verify(queryClient,times(2)).query(anyString(),anyString());verify(client,times(1)).create(anyMap(),anyString());
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void staleSyncCompletionCannotOverwriteNewerObservation() {
        var order=fixture(true,true,"ALIEXPRESS");service.send(order.getId());
        var row=fulfillments.findByOrderId(order.getId()).orElseThrow();
        String old=row.beginSupplierSync(Instant.now().minusSeconds(121));String current=row.beginSupplierSync(Instant.now());
        var fresh=row.getSupplierOrderIds().stream().map(id->AliExpressSupplierSnapshot.classify(id,"WAIT_BUYER_ACCEPT_GOODS",null)).toList();
        row.finishSupplierSync(current,row.getSupplierOrderIds(),fresh,Instant.now());
        var stale=row.getSupplierOrderIds().stream().map(id->AliExpressSupplierSnapshot.classify(id,"PLACE_ORDER_SUCCESS",null)).toList();
        row.finishSupplierSync(old,row.getSupplierOrderIds(),stale,Instant.now());
        assertThat(row.getSupplierStatus()).isEqualTo("SHIPPED");
    }
    private Order fixture(boolean paid,boolean complete,String supplier) {
        var user=users.save(User.local("Cliente Teste","ali-order@example.test","hash"));
        var address=addresses.save(new Address(user,"Casa","Cliente Teste","89229040","Rua Teste","10",null,"Centro","Joinville","SC",true));
        var product=new Product("Produto","ali-product","Descrição","Descrição",new BigDecimal("50.00"),null,"Acessórios",0,null,true);
        product.linkSupplier(supplier,"1005010661577657",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        var variant=new ProductVariant(product,supplier,"12000053109276547","1005010661577657","TEST-SKU","Azul",Map.of("14","Azul","14:valueId","771"),"14:771#Azul",new BigDecimal("25.39"),"BRL",null,null,null,null,null);
        variants.saveAndFlush(variant);
        var leg=new ShippingProvider.Leg(supplier,"ALI-CONFIRMED","TEST-SERVICE","CN",new BigDecimal("10"),"BRL",new BigDecimal("10"),15,List.of("12000053109276547"));
        var option=new ShippingProvider.Option("ALI-CONFIRMED","Frete confirmado",new BigDecimal("10"),15,supplier,new BigDecimal("10"),"BRL",List.of(leg));
        var order=new Order(user,address,new BigDecimal("50"),option,Duration.ofHours(24));
        order.addItem(new OrderItem(order,product,variant,1,new BigDecimal("50"),new BigDecimal("50")));
        if(complete) {ReflectionTestUtils.setField(order,"recipientCpf","52998224725");ReflectionTestUtils.setField(order,"recipientBirthDate",LocalDate.of(1990,1,1));ReflectionTestUtils.setField(order,"recipientPhone","47999999999");}
        if(paid) order.markPaid();orders.saveAndFlush(order);initializer.initialize(order);
        var payment=new Payment(order,PaymentMethod.MERCADO_PAGO);
        if(paid) {payment.synchronizeCheckoutPayment("synthetic-payment",PaymentStatus.PAID,Instant.now(),"pix","pix");payment.recordProviderEnvironment(true,Instant.now());}
        payments.saveAndFlush(payment);return order;
    }
    private String path(Order order) {return "/api/admin/orders/"+order.getId()+"/aliexpress/send";}
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
}

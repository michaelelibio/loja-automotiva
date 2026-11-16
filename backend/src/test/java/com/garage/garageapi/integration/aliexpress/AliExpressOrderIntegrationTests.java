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
        "aliexpress.orders.sync-enabled=true","aliexpress.orders.sync-contract-confirmed=true",
        "aliexpress.orders.auto-dispatch-enabled=true","aliexpress.orders.auto-dispatch-created-from=2000-01-01T00:00:00Z"})
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
    @MockitoBean AliExpressProductClient productClient;
    @MockitoBean AliExpressProductNormalizer normalizer;
    @MockitoBean com.garage.garageapi.payment.gateway.CheckoutProGateway mercadoPago;
    @MockitoBean AliExpressDispatchWorker worker;
    @Autowired AliExpressDispatchValidation validation;
    @Autowired AliExpressAutomationPolicy automation;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.garage.garageapi.payment.service.CheckoutProWebhookUpdater webhook;
    @Autowired AdminAliExpressTestRunRepository adminRuns;
    @Autowired AliExpressDispatchQuoteService quoteService;
    @Autowired AliExpressDispatchRecordRepository secondary;
    @Autowired AliExpressRecipientAdminService recipientAdmin;
    @Autowired AliExpressRecipientAdminAuditRepository recipientAudits;

    @BeforeEach void clean() {
        reset(client,tokens,initializer,fulfillments);
        reset(queryClient,queryPermission);
        reset(mercadoPago,productClient,normalizer,worker);
        adminRuns.deleteAll();
        recipientAudits.deleteAll();
        secondary.deleteAll();
        when(queryPermission.verified()).thenReturn(true);
        when(permission.verified()).thenReturn(true);
        fulfillments.deleteAll();payments.deleteAll();orders.deleteAll();media.deleteAll();variants.deleteAll();products.deleteAll();addresses.deleteAll();users.deleteAll();
        when(tokens.accessToken()).thenReturn("synthetic-token-never-log");
        when(tokens.dispatchAccess()).thenReturn(new AliExpressTokenService.DispatchAccess("synthetic-token-never-log",AliExpressOAuthStateService.hash("synthetic-buyer")));
        when(tokens.buyerIdentity()).thenReturn(new AliExpressTokenService.BuyerIdentity("synthetic-buyer",null));
        when(productClient.get(anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"12000053109276547\",\"sku_attr\":\"14:771#Azul\",\"sku_available_stock\":10,\"offer_sale_price\":\"25.39\",\"currency_code\":\"BRL\"}]}"));
        when(normalizer.normalize(any(),anyString(),anyString())).thenAnswer(inv->new AliExpressProductPreview(
                inv.getArgument(1),"Produto","Descrição","ALIEXPRESS","BRL",List.of(),
                List.of(new AliExpressProductPreview.Variant("12000053109276547","TEST-SKU","14:771#Azul",Map.of(),List.of(),new BigDecimal("25.39"),new BigDecimal("25.39"),"BRL",10)),
                Map.of(),Map.of(),Map.of(),Map.of(),"BR",null,false,List.of(),"synthetic-fingerprint"));
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
                .andExpect(jsonPath("$.supplierStatus").value("CREATED_UNPAID_UNVERIFIED"))
                .andExpect(jsonPath("$.supplierOrderIds.length()").value(2));
        var state=service.view(order.getId());assertThat(state.canSend()).isFalse();
        mvc.perform(post(path(order)).with(admin())).andExpect(status().isOk());
        verify(client,times(1)).create(argThat(payload -> payload.keySet().equals(Set.of("out_order_id","ds_extend_request","logistics_address","product_items"))
                && payload.get("ds_extend_request").equals(Map.of("trade_extra_param",Map.of("birthday","01011990")))),anyString());
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
        verify(client,times(2)).create(argThat(payload -> payload.keySet().equals(Set.of("out_order_id","ds_extend_request","logistics_address","product_items"))),anyString());
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
    @ParameterizedTest @ValueSource(strings={"recipientCpf","recipientBirthDate","recipientPhone"})
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
    @ParameterizedTest @ValueSource(strings={"cpf","birthDate","phone","address","quantity","sku","rawSku"})
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
    @Test void approvedWebhookCreatesAfterCommitAndRepeatedWebhookDoesNotCreateAgain() {
        var order=fixture(false,true,"ALIEXPRESS");
        doAnswer(inv->{service.sendAutomatically(inv.getArgument(0));return null;}).when(worker).enqueue(anyLong());
        var result=mercadoPago.findPayment("synthetic-payment");
        webhook.apply("synthetic-payment",result);webhook.apply("synthetic-payment",result);
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(service.view(order.getId()).attemptCount()).isEqualTo(1);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void recoveryScanFindsCommittedPaidOrderWhenAfterCommitCallbackWasLost() {
        var order=fixture(true,true,"ALIEXPRESS");
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void historicalPaidOrderIsExcludedFromRecoveryAndDirectAutomaticEntry() {
        var order=fixture(true,true,"ALIEXPRESS");
        makeHistorical(order);
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).automaticBlockReason()).contains("Reconcilie");
        assertThat(service.view(order.getId()).attemptCount()).isZero();
        verifyNoInteractions(client,tokens,mercadoPago,productClient);
    }
    @Test void approvalWebhookForHistoricalOrderDoesNotSendTheOldPurchase() {
        var order=fixture(false,true,"ALIEXPRESS");
        makeHistorical(order);
        doAnswer(inv->{service.sendAutomatically(inv.getArgument(0));return null;}).when(worker).enqueue(anyLong());
        var result=mercadoPago.findPayment("synthetic-payment");
        webhook.apply("synthetic-payment",result);webhook.apply("synthetic-payment",result);
        assertThat(service.view(order.getId()).paymentConfirmed()).isTrue();
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(service.view(order.getId()).attemptCount()).isZero();
        verifyNoInteractions(client,tokens,productClient);
    }
    @Test void historicalMixedOrderCannotEnterTheSecondaryRecoveryStream() {
        var order=mixedFixture();
        makeHistorical(order);
        var recovery=new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation);
        ReflectionTestUtils.setField(recovery,"secondary",secondary);recovery.recoverPending();
        service.sendAutomatically(order.getId());
        assertThat(secondary.findById(order.getId())).isEmpty();
        assertThat(fulfillments.findByOrderId(order.getId()).orElseThrow().getProvider()).isEqualTo("MULTIPLE");
        verifyNoInteractions(client,tokens,mercadoPago,productClient);
    }
    @Test void committedPreflightFailureIsRecoveredByANewWorkerWithoutDuplicateCreation() {
        var order=fixture(true,true,"ALIEXPRESS");var approved=mercadoPago.findPayment("synthetic-payment");
        when(mercadoPago.findPayment("synthetic-payment"))
                .thenThrow(new com.garage.garageapi.payment.gateway.PaymentProviderException("private-provider-body"))
                .thenReturn(approved);
        service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).attemptCount()).isZero();
        assertThat(service.view(order.getId()).lastError()).doesNotContain("private-provider-body");
        var row=fulfillments.findByOrderId(order.getId()).orElseThrow();
        ReflectionTestUtils.setField(row,"dispatchNextCheckAt",Instant.now().minusSeconds(1));fulfillments.saveAndFlush(row);
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void manualDataRequiresCurrentActiveAdminAndCannotBeReadByCustomerOrStaleRole() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");String url="/api/admin/orders/"+order.getId()+"/aliexpress/recipient/manual-data";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        var owner=jwt().jwt(j->j.subject(order.getUser().getId().toString()));
        mvc.perform(get(url).with(owner)).andExpect(status().isForbidden());
        mvc.perform(get(url).with(owner.authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isForbidden());
        var actor=users.saveAndFlush(User.local("Admin Teste","manual-admin@example.test","synthetic"));
        ReflectionTestUtils.setField(actor,"role",com.garage.garageapi.user.entity.UserRole.ADMIN);users.saveAndFlush(actor);
        var token=jwt().jwt(j->j.subject(actor.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get(url).with(token)).andExpect(status().isOk());
        ReflectionTestUtils.setField(actor,"active",false);users.saveAndFlush(actor);
        mvc.perform(get(url).with(token)).andExpect(status().isForbidden());
        verifyNoInteractions(client,tokens,mercadoPago,productClient);
    }
    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void manualDataReadsOnlyOrderSnapshotWithPrivateCacheAndNoSideEffectsOrPersonalLogs(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        var actor=users.saveAndFlush(User.local("Admin Teste","manual-admin@example.test","synthetic"));
        ReflectionTestUtils.setField(actor,"role",com.garage.garageapi.user.entity.UserRole.ADMIN);users.saveAndFlush(actor);
        var currentAddress=addresses.findAll().get(0);ReflectionTestUtils.setField(currentAddress,"recipientName","Outra Pessoa");addresses.saveAndFlush(currentAddress);
        var token=jwt().jwt(j->j.subject(actor.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/admin/orders/"+order.getId()+"/aliexpress/recipient/manual-data").with(token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("private"),org.hamcrest.Matchers.containsString("no-store"))))
                .andExpect(result->assertThat(result.getResponse().getHeaders("Vary")).contains("Authorization"))
                .andExpect(jsonPath("$.recipientName").value("Cliente Teste")).andExpect(jsonPath("$.cpf").value("52998224725"))
                .andExpect(jsonPath("$.birthDate").value("01/01/1990")).andExpect(jsonPath("$.phone").value("47999999999"))
                .andExpect(jsonPath("$.country").value("BR")).andExpect(jsonPath("$.street").value("Rua Teste"))
                .andExpect(jsonPath("$.items[0].supplierProductId").value("1005010661577657"))
                .andExpect(jsonPath("$.items[0].supplierVariantId").value("12000053109276547"))
                .andExpect(jsonPath("$.items[0].quantity").value(1)).andExpect(jsonPath("$.supplierOrderIds").isEmpty());
        assertThat(output.getAll()).doesNotContain("52998224725","01/01/1990","47999999999");
        var details=recipientAdmin.manualData(order.getId(),org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","none").subject(actor.getId().toString()).build());
        assertThat(details.toString()).doesNotContain("52998224725","01/01/1990","47999999999");
        assertThat(fulfillments.findByOrderId(order.getId()).orElseThrow().getAttemptCount()).isZero();
        assertThat(recipientAudits.count()).isZero();assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(client,tokens,mercadoPago,productClient);
    }
    @Test void cpfMismatchSurvivesRecoveryAndRepeatedEventsWithoutResendingOrChangingRecipient() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),false,false,
                new AliExpressOrderClient.ProviderError("B_DROPSHIPPER_DELIVERY_ADDRESS_CPF_NOT_MATCH",null,"CPF mismatch",200)));
        service.sendAutomatically(order.getId());
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        service.sendAutomatically(order.getId());
        var state=service.view(order.getId());
        assertThat(state.status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThat(state.attemptCount()).isEqualTo(1);assertThat(state.canSend()).isFalse();
        assertThat(state.lastError()).contains("B_DROPSHIPPER_DELIVERY_ADDRESS_CPF_NOT_MATCH").doesNotContain("52998224725");
        assertThat(state.supplierOrderIds()).isEmpty();
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        var saved=orders.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getRecipientCpf()).isEqualTo("52998224725");
        assertThat(saved.getRecipientBirthDate()).isEqualTo(LocalDate.of(1990,1,1));
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void automatedDispatchNeverRetriesRejectedOrUnknownPurchase() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),true));
        service.sendAutomatically(order.getId());service.sendAutomatically(order.getId());
        new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation).recoverPending();
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.FAILED);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void missingQuoteFreightAndTaxesNeverBlockAutomaticCreation() {
        var order=fixture(true,true,"ALIEXPRESS");
        var row=fulfillments.findByOrderId(order.getId()).orElseThrow();ReflectionTestUtils.setField(row,"dispatchQuote",null);fulfillments.saveAndFlush(row);
        service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(service.view(order.getId()).attemptCount()).isEqualTo(1);
        assertThat(service.view(order.getId()).financialWarnings()).anyMatch(w->w.contains("Frete")).anyMatch(w->w.contains("Impostos"));
        service.sendAutomatically(order.getId());
        verify(client,times(1)).create(argThat(p->((List<Map<String,Object>>)p.get("product_items")).stream().noneMatch(l->l.containsKey("logistics_service_name"))),anyString());
    }
    @Test void currentUnapprovedPaymentBlocksEvenWhenDatabaseSaysPaid() {
        var order=fixture(true,true,"ALIEXPRESS");
        var current=mercadoPago.findPayment("synthetic-payment");
        when(mercadoPago.findPayment("synthetic-payment")).thenReturn(new com.garage.garageapi.payment.gateway.CheckoutProGateway.PaymentResult(
                current.providerPaymentId(),current.externalReference(),PaymentStatus.CANCELED,"refunded",current.transactionAmount(),"BRL","pix","pix",null,true));
        service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).lastError()).contains("Pagamento atual");
        verifyNoInteractions(client,productClient);
    }
    @Test void preflightCommunicationRetriesAreBoundedAndDoNotConsumePurchaseAttempts() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(productClient.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("private-token-never-log"));
        for(int i=0;i<3;i++) {
            service.sendAutomatically(order.getId());
            var row=fulfillments.findByOrderId(order.getId()).orElseThrow();
            if(i<2) {ReflectionTestUtils.setField(row,"dispatchNextCheckAt",Instant.now().minusSeconds(1));fulfillments.saveAndFlush(row);}
        }
        service.sendAutomatically(order.getId());
        var state=service.view(order.getId());
        assertThat(state.validationAttemptCount()).isEqualTo(3);assertThat(state.attemptCount()).isZero();
        assertThat(state.nextValidationAt()).isNull();assertThat(state.lastError()).doesNotContain("private-token");
        verify(productClient,times(3)).get(anyString(),anyString(),anyString(),anyString(),anyString());verifyNoInteractions(client);
    }
    @Test void administrativeAttemptAndPartialSupplierIdsPreventNormalCreation() {
        var order=fixture(true,true,"ALIEXPRESS");
        var admin=new AdminAliExpressTestRun(order.getId());admin.realKey="legacy-real-purchase";admin.supplierOrderIds=List.of("99111");adminRuns.saveAndFlush(admin);
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void threeDefinitiveRejectionsBlockFurtherManualAttempts() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),true));
        for(int i=0;i<3;i++)service.send(order.getId());
        assertThat(service.view(order.getId()).attemptCount()).isEqualTo(3);
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verify(client,times(3)).create(anyMap(),anyString());
    }
    @Test void partialResponseIdsRemainVisibleButNeverUnlockAnotherCreation() {
        var order=fixture(true,true,"ALIEXPRESS");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("99112"),false,false));
        var state=service.send(order.getId());assertThat(state.status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThat(state.supplierOrderIds()).containsExactly("99112");
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void quoteApprovalRequiresCompleteExplicitCostsAndNeverCreatesAPurchase() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");
        var quote=new AliExpressDispatchQuoteService.Request(order.quoteContextDigest(),
                List.of(new AliExpressDispatchQuoteService.Line("12000053109276547",new BigDecimal("25.39"),"TEST-SERVICE")),
                BigDecimal.TEN,BigDecimal.ZERO,new BigDecimal("35.39"),Instant.now().plusSeconds(300),"supplier-check-21",true,true,false);
        var json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(quote);
        mvc.perform(post("/api/admin/orders/"+order.getId()+"/aliexpress/dispatch-quote").contentType("application/json").content(json)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/orders/"+order.getId()+"/aliexpress/dispatch-quote").with(admin()).contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dispatchQuote.total").value(35.39));
        verifyNoInteractions(client);
        var incomplete=new AliExpressDispatchQuoteService.Request(order.quoteContextDigest(),quote.lines(),BigDecimal.TEN,null,quote.total(),quote.validUntil(),quote.evidenceReference(),true,true,false);
        mvc.perform(post("/api/admin/orders/"+order.getId()+"/aliexpress/dispatch-quote").with(admin()).contentType("application/json")
                .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(incomplete))).andExpect(status().isBadRequest());
    }
    @Test void expiredQuoteDoesNotBlockAndItsDeliveryServiceIsOmitted() {
        var order=fixture(true,true,"ALIEXPRESS");var row=fulfillments.findByOrderId(order.getId()).orElseThrow();var q=row.getDispatchQuote();
        row.approveDispatch(new AliExpressDispatchQuote(q.contextDigest(),q.buyerHash(),q.appHash(),q.lines(),q.productTotal(),q.shippingTotal(),q.taxTotal(),q.total(),q.verifiedAt(),Instant.now().minusSeconds(1),q.verifiedBy(),q.evidenceReference()));
        fulfillments.saveAndFlush(row);
        assertThat(service.send(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        verify(client).create(argThat(p->((List<Map<String,Object>>)p.get("product_items")).stream().noneMatch(l->l.containsKey("logistics_service_name"))),anyString());
    }
    @Test void unconfirmedOrSandboxPaymentCannotPoisonFutureConfirmedDispatch() {
        var order=fixture(false,true,"ALIEXPRESS");service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).lastError()).isNull();
        doAnswer(inv->{service.sendAutomatically(inv.getArgument(0));return null;}).when(worker).enqueue(anyLong());
        webhook.apply("synthetic-payment",mercadoPago.findPayment("synthetic-payment"));
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void legacyPaidOrderWithoutFulfillmentIsRecoveredInSeparateSupplierStream() {
        var order=fixture(true,true,"ALIEXPRESS");
        fulfillments.deleteAll();
        assertThat(service.send(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(secondary.findById(order.getId())).isPresent();
        verify(client).create(anyMap(),anyString());
    }
    @Test void mixedOrderDispatchesOnlyAliItemsAndPreservesOtherSupplierState() {
        var order=mixedFixture();
        var original=fulfillments.findByOrderId(order.getId()).orElseThrow();
        service.sendAutomatically(order.getId());service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(secondary.findById(order.getId()).orElseThrow().restore().getAttemptCount()).isEqualTo(1);
        var unchanged=fulfillments.findByOrderId(order.getId()).orElseThrow();
        assertThat(unchanged.getProvider()).isEqualTo(original.getProvider());assertThat(unchanged.getStatus()).isEqualTo(original.getStatus());
        assertThat(unchanged.getSupplierOrderId()).isEqualTo(original.getSupplierOrderId());assertThat(unchanged.getAttemptCount()).isZero();
        verify(client).create(argThat(p->((List<Map<String,Object>>)p.get("product_items")).size()==1),anyString());
    }
    @Test void mixedOrderTimeoutSurvivesReloadAndRecoveryNeverRecreatesIt() {
        var order=mixedFixture();when(client.create(anyMap(),anyString())).thenReturn(AliExpressOrderClient.Result.unknown());
        var worker=new AliExpressDispatchWorker(fulfillments,service,Runnable::run,automation);ReflectionTestUtils.setField(worker,"secondary",secondary);
        worker.recoverPending();worker.recoverPending();service.sendAutomatically(order.getId());
        assertThat(service.view(order.getId()).status()).isEqualTo(FulfillmentStatus.VERIFICATION_REQUIRED);
        assertThat(secondary.findById(order.getId()).orElseThrow().restore().getAttemptCount()).isEqualTo(1);
        assertThat(fulfillments.findByOrderId(order.getId()).orElseThrow().getStatus()).isEqualTo(FulfillmentStatus.PENDING);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lossAndUnquotedShippingAreAlertsOnly(boolean automatic) {
        var order=fixture(true,true,"ALIEXPRESS");
        ReflectionTestUtils.setField(order,"shippingLegs",null);orders.saveAndFlush(order);
        when(productClient.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(
            tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"12000053109276547\",\"sku_attr\":\"14:771#Azul\",\"sku_available_stock\":10,\"offer_sale_price\":\"999.00\",\"currency_code\":\"BRL\"}]}"));
        if(automatic)service.sendAutomatically(order.getId());else service.send(order.getId());
        var view=service.view(order.getId());assertThat(view.status()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(view.supplierCosts().productTotal()).isEqualByComparingTo("999.00");assertThat(view.supplierCosts().total()).isNull();
        assertThat(view.financialWarnings()).anyMatch(w->w.contains("superior"));
        verify(client).create(anyMap(),anyString());
    }
    private void makeHistorical(Order order) {
        // created_at is immutable through JPA; modify only this isolated synthetic test fixture.
        jdbc.update("update orders set created_at=? where id=?",java.sql.Timestamp.from(Instant.parse("1999-12-31T23:59:59Z")),order.getId());
    }
    private Order mixedFixture() {
        var order=fixture(true,true,"ALIEXPRESS");
        var product=new Product("Outro","other","Descrição","Descrição",BigDecimal.TEN,null,"Acessórios",0,null,true);
        product.linkSupplier("CJ","cj-product",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        order.addItem(new OrderItem(order,product,1,BigDecimal.TEN,BigDecimal.TEN));orders.saveAndFlush(order);
        var original=fulfillments.findByOrderId(order.getId()).orElseThrow();ReflectionTestUtils.setField(original,"provider","MULTIPLE");fulfillments.saveAndFlush(original);
        return order;
    }
    @Test void recipientAdminEndpointsRequireAdministratorAndNeverRevealPersonalValues() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");String path="/api/admin/orders/"+order.getId()+"/aliexpress/recipient";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        var customer=jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(get(path).with(customer)).andExpect(status().isForbidden());
        mvc.perform(patch(path).with(customer).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post(path+"/reconcile").with(customer).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get(path).with(admin())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.maskedCpf").value("***.***.***-25")).andExpect(jsonPath("$.birthDatePresent").value(true))
                .andExpect(jsonPath("$.cpf").doesNotExist()).andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("52998224725"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1990-01-01"))));
        verifyNoInteractions(client,tokens,mercadoPago,productClient);
    }
    @Test void correctionChangesOnlyOrderSnapshotAndManualReconciliationNeverEnqueuesOrCreates() throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");var address=addresses.findAll().get(0);
        String originalStreet=address.getStreet(),originalName=address.getRecipientName(),email=order.getUser().getEmail();
        var corrected=recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("111.444.777-35","02/02/1992",order.quoteContextDigest()),"synthetic-admin");
        assertThat(corrected.maskedCpf()).isEqualTo("***.***.***-35");assertThat(corrected.birthDatePresent()).isTrue();assertThat(corrected.manualHold()).isTrue();
        service.sendAutomatically(order.getId());assertThat(service.view(order.getId()).canSend()).isFalse();
        var reconciled=recipientAdmin.reconcile(order.getId(),new AliExpressRecipientAdminService.Reconciliation(corrected.contextDigest(),0,corrected.recipientRevision(),true,true,true),"synthetic-admin");
        assertThat(reconciled.manualHold()).isTrue();assertThat(reconciled.manualRetryReady()).isTrue();
        service.sendAutomatically(order.getId());verifyNoInteractions(client,tokens,worker);
        assertThat(recipientAudits.count()).isEqualTo(2);
        assertThat(addresses.findById(address.getId()).orElseThrow().getStreet()).isEqualTo(originalStreet);
        assertThat(addresses.findById(address.getId()).orElseThrow().getRecipientName()).isEqualTo(originalName);
        assertThat(users.findById(order.getUser().getId()).orElseThrow().getEmail()).isEqualTo(email);
        service.send(order.getId());service.send(order.getId());
        verify(client,times(1)).create(argThat(p->p.get("ds_extend_request").equals(Map.of("trade_extra_param",Map.of("birthday","02021992")))
                && ((Map<?,?>)p.get("logistics_address")).get("cpf").equals("11144477735")),anyString());
        assertThat(recipientAdmin.view(order.getId()).canEdit()).isFalse();
    }
    @Test void ambiguousAttemptRequiresCorrectionCurrentRevisionAndExplicitAbsenceBeforeManualRetry() {
        var order=mixedFixture();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(s->{var loaded=orders.findByIdForLifecycleUpdate(order.getId()).orElseThrow();var row=dispatchStore.locked(loaded);row.beginAliExpress(Instant.now().minusSeconds(180));dispatchStore.flush(row);});
        var current=recipientAdmin.view(order.getId());
        assertThatThrownBy(()->recipientAdmin.reconcile(order.getId(),new AliExpressRecipientAdminService.Reconciliation(current.contextDigest(),1,1L,true,true,true),"admin"))
                .isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        var corrected=recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("11144477735","02/02/1992",current.contextDigest()),"admin");
        for(var invalid:List.of(new AliExpressRecipientAdminService.Reconciliation(corrected.contextDigest(),0,corrected.recipientRevision(),true,true,true),
                new AliExpressRecipientAdminService.Reconciliation(corrected.contextDigest(),1,corrected.recipientRevision(),true,false,true),
                new AliExpressRecipientAdminService.Reconciliation(current.contextDigest(),1,corrected.recipientRevision(),true,true,true))) {
            assertThatThrownBy(()->recipientAdmin.reconcile(order.getId(),invalid,"admin")).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        }
        recipientAdmin.reconcile(order.getId(),new AliExpressRecipientAdminService.Reconciliation(corrected.contextDigest(),1,corrected.recipientRevision(),true,true,true),"admin");
        service.sendAutomatically(order.getId());verifyNoInteractions(client,tokens);
        service.send(order.getId());assertThat(service.view(order.getId()).attemptCount()).isEqualTo(2);
        assertThat(fulfillments.findByOrderId(order.getId()).orElseThrow().getProvider()).isEqualTo("MULTIPLE");
    }
    @Autowired AliExpressDispatchStore dispatchStore;
    @ParameterizedTest @ValueSource(strings={"cpf","date","missing","future","old"})
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void invalidRecipientCorrectionHasFixedErrorsAndDoesNotLogPersonalValues(String kind,org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var order=fixture(true,true,"ALIEXPRESS");String cpf="cpf".equals(kind)?"11111111111":"11144477735";
        String date=switch(kind){case "date"->"31/02/1992";case "missing"->"";case "future"->"01/01/2999";case "old"->"01/01/1800";default->"02/02/1992";};
        var request=new AliExpressRecipientAdminService.Correction(cpf,date,order.quoteContextDigest());
        assertThat(request.toString()).doesNotContain(cpf,date.isEmpty()?"birthDate=":date);
        mvc.perform(patch("/api/admin/orders/"+order.getId()+"/aliexpress/recipient").with(admin()).contentType("application/json")
                .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(cpf))));
        assertThat(orders.findById(order.getId()).orElseThrow().getRecipientCpf()).isEqualTo("52998224725");
        assertThat(recipientAudits.count()).isZero();assertThat(output.getAll()).doesNotContain(cpf);
        if(!date.isEmpty())assertThat(output.getAll()).doesNotContain(date);
        verifyNoInteractions(client,tokens);
    }
    @Test void activeAttemptExternalIdsAndStaleCorrectionAreNeverReleased() {
        var order=fixture(true,true,"ALIEXPRESS");var row=fulfillments.findByOrderId(order.getId()).orElseThrow();row.beginAliExpress(Instant.now());fulfillments.saveAndFlush(row);
        assertThatThrownBy(()->recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("11144477735","02/02/1992",order.quoteContextDigest()),"admin"))
                .isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        assertThatThrownBy(()->recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("11144477735","02/02/1992","0".repeat(64)),"admin"))
                .isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void supplierAcceptanceCannotLoseItsIdBecauseOfAnOldAssistedFinancialGate() {
        var order=fixture(true,true,"ALIEXPRESS");ReflectionTestUtils.setField(order,"assistedQuote",com.garage.garageapi.order.quote.AssistedQuote.waiting());orders.saveAndFlush(order);
        assertThat(service.send(order.getId()).supplierOrderIds()).hasSize(2);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }
    @Test void manualReleaseExpiresAndAnotherCorrectionInvalidatesItsRevision() {
        var order=fixture(true,true,"ALIEXPRESS");
        var corrected=recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("11144477735","02/02/1992",order.quoteContextDigest()),"admin");
        var check=new AliExpressRecipientAdminService.Reconciliation(corrected.contextDigest(),0,corrected.recipientRevision(),true,true,true);
        recipientAdmin.reconcile(order.getId(),check,"admin");
        var row=fulfillments.findByOrderId(order.getId()).orElseThrow();ReflectionTestUtils.setField(row,"dispatchReviewAt",Instant.now().minusSeconds(901));fulfillments.saveAndFlush(row);
        assertThatThrownBy(()->service.send(order.getId())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        recipientAdmin.correct(order.getId(),new AliExpressRecipientAdminService.Correction("11144477735","02/02/1992",corrected.contextDigest()),"admin");
        assertThatThrownBy(()->recipientAdmin.reconcile(order.getId(),check,"admin")).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        service.sendAutomatically(order.getId());verifyNoInteractions(client,tokens);
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
        if("ALIEXPRESS".equals(supplier)) {
            var row=fulfillments.findByOrderId(order.getId()).orElseThrow();
            row.approveDispatch(new AliExpressDispatchQuote(order.quoteContextDigest(),AliExpressOAuthStateService.hash("synthetic-buyer"),validation.appHash(),
                    List.of(new AliExpressDispatchQuote.Line("1005010661577657","12000053109276547","14:771#Azul",1,new BigDecimal("25.39"),"TEST-SERVICE")),
                    new BigDecimal("25.39"),BigDecimal.TEN,BigDecimal.ZERO,new BigDecimal("35.39"),Instant.now(),Instant.now().plusSeconds(600),"synthetic-admin","synthetic-supplier-quote"));
            fulfillments.saveAndFlush(row);
        }
        var payment=new Payment(order,PaymentMethod.MERCADO_PAGO);
        if(paid) {payment.synchronizeCheckoutPayment("synthetic-payment",PaymentStatus.PAID,Instant.now(),"pix","pix");payment.recordProviderEnvironment(true,Instant.now());}
        payments.saveAndFlush(payment);
        String reference="garage_order_"+order.getId()+"_payment_"+payment.getId();
        payment.applyCheckoutPreference("synthetic-pref-"+payment.getId(),reference,"https://example.test/pay");payments.saveAndFlush(payment);
        when(mercadoPago.findPayment("synthetic-payment")).thenReturn(new com.garage.garageapi.payment.gateway.CheckoutProGateway.PaymentResult(
                "synthetic-payment",reference,PaymentStatus.PAID,"approved",order.getTotal(),"BRL","pix","pix",Instant.now(),true));
        return order;
    }
    private String path(Order order) {return "/api/admin/orders/"+order.getId()+"/aliexpress/send";}
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
}

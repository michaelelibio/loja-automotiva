package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAliExpressTestIntegrationTests {
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired OrderFulfillmentRepository fulfillments;
    @Autowired AdminAliExpressTestRunRepository runs;
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired com.garage.garageapi.product.repository.ProductRepository products;
    @Autowired PlatformTransactionManager manager;
    @Autowired Environment environment;
    @Autowired MockMvc mvc;
    @Autowired AliExpressOrderService normalSupplier;
    @Autowired AdminAliExpressTestService configuredService;
    @MockitoBean AliExpressOrderClient client;
    @MockitoBean AliExpressTokenService tokens;
    private AliExpressTestReadiness readiness;
    private Order order;
    private AliExpressTestFixture fixture;

    @BeforeEach void setup() {
        reset(client,tokens); readiness=mock(AliExpressTestReadiness.class);
        when(readiness.blockers(any())).thenReturn(List.of());
        when(tokens.accessToken()).thenReturn("synthetic-token");
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("900001"),false));
        String unique=UUID.randomUUID().toString();
        var user=users.save(User.local("Test admin flow",unique+"@example.test","hash"));
        var address=addresses.save(new Address(user,"Cliente","CLIENT DATA MUST NEVER BE SENT","89229040","Customer street","10",null,"Centro","Joinville","SC",true));
        var product=new com.garage.garageapi.product.entity.Product("Test product","test-"+unique,"Test","Test",
                new BigDecimal("50"),null,"Test",0,null,true);
        product.linkSupplier("ALIEXPRESS",new java.math.BigInteger(unique.replace("-",""),16).toString().substring(0,20),null,null,Instant.now());
        product.configureFulfillment(com.garage.garageapi.product.entity.FulfillmentType.DROPSHIPPING);
        products.saveAndFlush(product);
        order=new Order(user,address,new BigDecimal("50"),new BigDecimal("10"),Duration.ofHours(24));
        order.addItem(new OrderItem(order,product,1,new BigDecimal("50"),new BigDecimal("50")));
        orders.saveAndFlush(order);
        fixture=new AliExpressTestFixture("fixture-"+unique,"synthetic-authorization","1005000000000000","123456","14:771",1,
                "SYNTHETIC_SERVICE","BRL",new BigDecimal("1"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1"),
                Map.of("country","BR","province","Santa Catarina","city","TEST CITY","address","OWN TEST ADDRESS",
                    "zip","89229040","contact_person","TEST OWNER","full_name","TEST OWNER","cpf","52998224725","mobile_no","47999999999"));
    }
    private AdminAliExpressTestService service(boolean enabled, Environment env) {
        return new AdminAliExpressTestService(orders,runs,readiness,client,tokens,manager,env,order.getId().toString(),enabled,order.getId(),
                tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(fixture));
    }
    private AdminAliExpressTestService.Confirmation confirmation() {
        return new AdminAliExpressTestService.Confirmation(AdminAliExpressTestService.CONFIRMATION,fixture.id(),fixture.digest());
    }
    @Test void completeSimulationPersistsSeparatelyAndCannotAuthorizeNormalPurchase() {
        var service=service(false,environment);
        assertThatThrownBy(()->service.simulate(order.getId(),"admin",true)).isInstanceOf(ResourceConflictException.class);
        var first=service.simulate(order.getId(),"admin",false);
        assertThat(first.simulatedPaymentAt()).isNotNull();
        var persisted=service.view(order.getId());
        assertThat(service.simulate(order.getId(),"another-admin",false).simulatedPaymentAt()).isEqualTo(persisted.simulatedPaymentAt());
        var completed=service.simulate(order.getId(),"admin",true);
        assertThat(completed.simulatedSupplierId()).isEqualTo("SIM-AE-"+order.getId());
        assertThat(completed.simulatedPaymentBy()).isEqualTo("admin");
        assertThat(service.view(order.getId()).simulatedSupplierAt()).isNotNull();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(payments.findAll().stream().filter(p->p.getOrder().getId().equals(order.getId())).toList()).isEmpty();
        assertThat(fulfillments.findByOrderId(order.getId())).isEmpty();
        assertThat(normalSupplier.view(order.getId()).applicable()).isTrue();
        assertThat(normalSupplier.view(order.getId()).paymentConfirmed()).isFalse();
        assertThatThrownBy(()->normalSupplier.send(order.getId())).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void unmarkedOrderBlockedAndDefaultsCannotBeBypassedThroughHttp() throws Exception {
        var path="/api/admin/orders/"+order.getId()+"/aliexpress-tests";
        for (String action:List.of("/simulate-payment","/simulate-supplier","/create-real")) {
            var request=post(path+action).contentType("application/json").content("{}");
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(post(path+action).contentType("application/json").content("{}").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path+action).contentType("application/json").content("{}").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.testOrder").value(false)).andExpect(jsonPath("$.canCreateReal").value(false));
        verifyNoInteractions(client,tokens);
    }
    @Test void explicitConfirmationAndExactFixtureDigestRequired() {
        var service=service(true,environment);
        for (var invalid:List.of(new AdminAliExpressTestService.Confirmation("yes",fixture.id(),fixture.digest()),
                new AdminAliExpressTestService.Confirmation(AdminAliExpressTestService.CONFIRMATION,"other",fixture.digest()),
                new AdminAliExpressTestService.Confirmation(AdminAliExpressTestService.CONFIRMATION,fixture.id(),"stale")))
            assertThatThrownBy(()->service.createReal(order.getId(),"admin",invalid)).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void adminCanRunSimulationThroughHttpAndReloadTheAuditRecord() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(configuredService,"testOrderIds",Set.of(order.getId()));
        try {
            String path="/api/admin/orders/"+order.getId()+"/aliexpress-tests";
            var admin=jwt().jwt(j->j.subject("test-administrator")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
            mvc.perform(post(path+"/simulate-payment").with(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.simulatedPaymentBy").value("test-administrator"));
            mvc.perform(post(path+"/simulate-supplier").with(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.simulatedSupplierId").value("SIM-AE-"+order.getId()));
            mvc.perform(get(path).with(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.simulatedPaymentAt").isNotEmpty()).andExpect(jsonPath("$.simulatedSupplierAt").isNotEmpty())
                    .andExpect(jsonPath("$.canCreateReal").value(false));
            verifyNoInteractions(client,tokens);
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(configuredService,"testOrderIds",Set.of()); }
    }
    @Test void globalIdempotencyKeyCannotBeReusedOnAnotherOrder() {
        service(true,environment).createReal(order.getId(),"admin",confirmation());
        var another=orders.saveAndFlush(new Order(order.getUser(),addresses.findAll().stream()
                .filter(a->a.getUser().getId().equals(order.getUser().getId())).findFirst().orElseThrow(),
                BigDecimal.ONE,BigDecimal.ZERO,Duration.ofHours(24)));
        var otherService=new AdminAliExpressTestService(orders,runs,readiness,client,tokens,manager,environment,
                another.getId().toString(),true,another.getId(),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(fixture));
        assertThatThrownBy(()->otherService.createReal(another.getId(),"admin",confirmation()))
                .isInstanceOf(ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
        verify(tokens,times(1)).accessToken();
    }
    @Test void rejectionNeverReopensTheRealIdentifier() {
        var service=service(true,environment);
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),true));
        assertThat(service.createReal(order.getId(),"admin",confirmation()).realStatus()).isEqualTo("REJECTED_NO_RETRY");
        assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void controlledBlocksEvenWithEnabledFlagsAndMockedProof() {
        var controlled=new MockEnvironment(); controlled.setActiveProfiles("controlled");
        var service=service(true,controlled);
        assertThat(service.view(order.getId()).canCreateReal()).isFalse();
        assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void allReadinessFailuresAreHardGatesBeforeCredentialsOrSupplier() {
        var service=service(true,environment);
        when(readiness.blockers(any())).thenReturn(List.of("Unverified provider evidence"));
        assertThat(service.view(order.getId()).canCreateReal()).isFalse();
        assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(client,tokens);
    }
    @Test void syntheticRealCallUsesOnlyOwnedFixtureAndStoresIdsWithoutAnyPayment() {
        var service=service(true,environment);
        var state=service.createReal(order.getId(),"admin",confirmation());
        assertThat(state.realStatus()).isEqualTo("CREATED_UNPAID_UNVERIFIED");
        assertThat(state.supplierOrderIds()).containsExactly("900001");
        assertThat(state.createdAt()).isNotNull(); assertThat(state.submittedAt()).isNotNull();
        assertThat(state.submittedBy()).isEqualTo("admin");
        verify(client).create(eq(fixture.payload()),eq("synthetic-token"));
        assertThat(fixture.payload().toString()).doesNotContain("CLIENT DATA","Customer street");
        assertThat(state.simulatedPaymentAt()).isNull();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void timeoutIsDurablyBlockedAcrossNewServiceInstance() {
        var service=service(true,environment);
        when(client.create(anyMap(),anyString())).thenThrow(new RuntimeException("private-provider-data"));
        assertThat(service.createReal(order.getId(),"admin",confirmation()).realStatus()).isEqualTo("VERIFICATION_REQUIRED");
        assertThatThrownBy(()->service(true,environment).createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
        verify(client,times(1)).create(anyMap(),anyString());
    }
    @Test void concurrencyPersistsClaimBeforeNetworkAndPreventsResubmission() throws Exception {
        var service=service(true,environment);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(client.create(anyMap(),anyString())).thenAnswer(inv->{entered.countDown(); assertThat(release.await(10,TimeUnit.SECONDS)).isTrue(); return AliExpressOrderClient.Result.unknown();});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->service.createReal(order.getId(),"admin",confirmation()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(service.view(order.getId()).realStatus()).isEqualTo("VERIFICATION_REQUIRED");
            assertThatThrownBy(()->service.createReal(order.getId(),"admin",confirmation())).isInstanceOf(ResourceConflictException.class);
            release.countDown(); first.get(10,TimeUnit.SECONDS);
            verify(client,times(1)).create(anyMap(),anyString());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void concreteProofVerifierCannotBeEnabledByLocalTokenOrPermissionFlag() {
        var permission=mock(AliExpressPlaceOrderPermission.class);
        var auth=mock(AliExpressAuthorizationDiagnostics.class);
        when(permission.verified()).thenReturn(true);
        when(auth.inspect()).thenReturn(new AliExpressAuthorizationDiagnostics.Report(true,"LOCALLY_USABLE",Instant.now().plusSeconds(600),"NOT_VERIFIED","NOT_VERIFIED",false));
        assertThat(new AliExpressTestReadiness(permission,auth).blockers(fixture)).hasSize(2);
        assertThat(new AliExpressPlaceOrderPermission().verified()).isFalse();
    }
}

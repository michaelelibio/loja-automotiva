package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:adminorder19;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminAliExpressTestIntegrationTests {
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired OrderFulfillmentRepository fulfillments;
    @Autowired AdminAliExpressTestRunRepository runs;
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired PlatformTransactionManager manager;
    @Autowired Environment environment;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AliExpressOrderService normalSupplier;
    @MockitoBean AliExpressOrderClient client;
    @MockitoBean AliExpressTokenService tokens;
    @MockitoBean AliExpressOrderQueryClient queryClient;
    @MockitoBean AliExpressOrderQueryPermission queryPermission;
    private AliExpressTestReadiness readiness;
    private Order order;
    @BeforeEach void setup(){
        runs.deleteAll();fulfillments.deleteAll();payments.deleteAll();orders.deleteAll();variants.deleteAll();products.deleteAll();jdbc.execute("ALTER TABLE orders ALTER COLUMN id RESTART WITH 19");
        reset(client,tokens,queryClient,queryPermission);readiness=mock(AliExpressTestReadiness.class);
        when(readiness.check(any(),anyString())).thenAnswer(inv->{var base=inv.getArgument(0,AliExpressTestFixture.class);return new AliExpressTestReadiness.Check(base.withProductCost(new BigDecimal("125")),null,"Existing OAuth buyer, identity unknown","UNKNOWN",Instant.now(),Instant.now().plusSeconds(120),List.of("Shipping and taxes unknown; cost may exceed inGarage"),"LIVE_PRODUCT_READ");});
        when(tokens.accessToken()).thenReturn("synthetic-token");when(tokens.buyerIdentity()).thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED));
        when(client.createAdministrative(eq(19L),anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("900001"),false));
        var user=users.save(User.local("Test Owner",UUID.randomUUID()+"@example.test","hash"));
        var address=addresses.save(new Address(user,"Test","Test Owner","89229040","Own Test Street","10",null,"Centro","Joinville","SC",true));
        var product=new Product("Test product","test-"+UUID.randomUUID(),"Test","Test",new BigDecimal("50"),null,"Test",0,null,true);product.linkSupplier("ALIEXPRESS","1005000000000000",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        var variant=variants.saveAndFlush(new ProductVariant(product,"ALIEXPRESS","123456","1005000000000000",null,"Test SKU",Map.of(),"14:771",BigDecimal.ONE,"BRL",null,null,null,null,null));
        order=new Order(user,address,new BigDecimal("50"),new BigDecimal("10"),Duration.ofHours(24));order.addItem(new OrderItem(order,product,variant,1,new BigDecimal("50"),new BigDecimal("50")));
        order.snapshotRecipient(new com.garage.garageapi.order.recipient.AliExpressRecipientData.Normalized("52998224725",LocalDate.of(1990,1,1),"47999999999"));orders.saveAndFlush(order);assertThat(order.getId()).isEqualTo(19L);
    }
    private AdminAliExpressTestService service(boolean enabled,Environment env){return new AdminAliExpressTestService(orders,variants,runs,readiness,client,tokens,manager,env,"19,20",enabled);}
    private AdminAliExpressTestService.Confirmation confirmation(AdminAliExpressTestService service){var p=service.preflight(19L,"admin");return new AdminAliExpressTestService.Confirmation(AdminAliExpressTestService.CONFIRMATION,p.costs().id(),p.digest(),p.preflightId(),true);}
    private AdminAliExpressTestSyncService sync(AdminAliExpressTestService service){return new AdminAliExpressTestSyncService(service,runs,orders,queryClient,tokens,manager,true);}
    @Test void preparationUsesSavedOwnerOrderAndDoesNotCreateOrInventUnknownValues(){
        var service=service(true,environment);assertThat(service.view(19L).canCreateReal()).isTrue();var review=service.preflight(19L,"admin");
        assertThat(review.costs().productCost()).isEqualByComparingTo("125");assertThat(review.inGarageProductTotal()).isEqualByComparingTo("50");
        assertThat(review.costs().shippingCost()).isNull();assertThat(review.costs().taxCost()).isNull();assertThat(review.costs().totalCost()).isNull();assertThat(review.costs().deliveryService()).isNull();assertThat(review.buyerState()).isEqualTo("UNKNOWN");
        assertThat(review.payload().get("logistics_address")).isEqualTo(AliExpressOrderPreparation.logisticsAddress(order));
        assertThat(review.payload()).containsEntry("out_order_id","ingarage-test-19-v1").containsEntry("ds_extend_request",Map.of("trade_extra_param",Map.of("birthday","01011990")));
        assertThat(((List<?>)review.payload().get("product_items")).get(0).toString()).doesNotContain("logistics_service_name");
        assertThat(review.toString()).doesNotContain("Own Test Street","52998224725");verifyNoInteractions(client,queryClient);
    }
    @Test void administrativeRoutesRequireAdmin()throws Exception{
        for(String suffix:List.of("/payload-review","/buyer-identity","/preflight","/create-real","/sync")){
            var req=suffix.equals("/payload-review") || suffix.equals("/buyer-identity")?get("/api/admin/orders/19/aliexpress-tests"+suffix):post("/api/admin/orders/19/aliexpress-tests"+suffix);
            mvc.perform(req).andExpect(status().isUnauthorized());mvc.perform(req.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        }verifyNoInteractions(client,tokens,queryClient);
    }
    @Test void explicitOwnerExceptionWorksInControlledAtHigherCostWithoutIdentityOrMethodProof(){
        var controlled=new MockEnvironment();controlled.setActiveProfiles("controlled");var service=service(true,controlled);var result=service.createReal(19L,"admin",confirmation(service));
        assertThat(result.realStatus()).isEqualTo("CREATED_UNPAID_UNVERIFIED");assertThat(result.supplierOrderIds()).containsExactly("900001");assertThat(result.simulatedPaymentAt()).isNull();
        assertThat(orders.findById(19L).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);assertThat(payments.findAll()).isEmpty();assertThat(fulfillments.findByOrderId(19L)).isEmpty();
        assertThat(normalSupplier.view(19L).canSend()).isFalse();verify(client,never()).create(anyMap(),anyString());verifyNoInteractions(queryPermission);
    }
    @Test void disabledFlagOtherOrdersAndInvalidRequiredDataStayBlocked(){
        assertThat(service(false,environment).view(19L).canCreateReal()).isFalse();assertThatThrownBy(()->service(false,environment).preflight(19L,"admin")).isInstanceOf(ResourceConflictException.class);
        var other=orders.saveAndFlush(new Order(order.getUser(),addresses.findAll().get(0),BigDecimal.ONE,BigDecimal.ZERO,Duration.ofHours(1)));
        assertThat(service(true,environment).view(other.getId()).canCreateReal()).isFalse();assertThatThrownBy(()->service(true,environment).preflight(other.getId(),"admin")).isInstanceOf(ResourceConflictException.class);
        jdbc.update("update orders set recipient_cpf=null where id=19");assertThat(service(true,environment).view(19L).canCreateReal()).isFalse();verifyNoInteractions(client,tokens);
    }
    @Test void explicitConsentToUnknownAndHigherCostsIsRequiredServerSide(){
        var service=service(true,environment);var c=confirmation(service);var noConsent=new AdminAliExpressTestService.Confirmation(c.phrase(),c.idempotencyKey(),c.fixtureDigest(),c.preflightId(),false);
        assertThatThrownBy(()->service.createReal(19L,"admin",noConsent)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.createReal(19L,"other-admin",c)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(client);
    }
    @Test void changedTokenOrOrderRequiresNewExplicitReview(){
        var service=service(true,environment);var c=confirmation(service);when(tokens.accessToken()).thenReturn("different-token");assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);
        when(tokens.accessToken()).thenReturn("synthetic-token");jdbc.update("update orders set street='Changed street' where id=19");assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(client);
    }
    @Test void expiredOrMissingReviewCannotSubmit(){
        var service=service(true,environment);var c=new AdminAliExpressTestService.Confirmation(AdminAliExpressTestService.CONFIRMATION,"ingarage-test-19-v1","invented","invented",true);
        assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(tokens,client);
        var valid=confirmation(service);var row=runs.findById(19L).orElseThrow();var p=row.preflight;row.preflight=new AliExpressAdminPreflight(p.id(),p.actor(),p.fixtureDigest(),p.orderSnapshotDigest(),p.tokenSha256(),p.buyerUserId(),p.buyerAccount(),p.buyerState(),p.checkedAt(),Instant.EPOCH,p.fixture(),p.warnings(),p.productCostSource(),p.inGarageProductTotal());runs.saveAndFlush(row);
        assertThatThrownBy(()->service.createReal(19L,"admin",valid)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(client);
    }
    @Test void timeoutSurvivesServiceRestartAndNeverReopensAttempt(){var service=service(true,environment);var c=confirmation(service);when(client.createAdministrative(eq(19L),anyMap(),anyString())).thenThrow(new RuntimeException("private-data"));
        assertThat(service.createReal(19L,"admin",c).realStatus()).isEqualTo("VERIFICATION_REQUIRED");assertThatThrownBy(()->service(true,environment).createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service(true,environment).preflight(19L,"admin")).isInstanceOf(ResourceConflictException.class);verify(client,times(1)).createAdministrative(eq(19L),anyMap(),anyString());}
    @Test void officialPermissionRejectionIsStoredAndShownWithoutGenericNotVerified(){var service=service(true,environment);var c=confirmation(service);
        var error=new AliExpressOrderClient.ProviderError("29","isv.permission-denied","Insufficient permissions",403);when(client.createAdministrative(eq(19L),anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of(),true,false,error));
        var result=service.createReal(19L,"admin",c);assertThat(result.realStatus()).isEqualTo("REJECTED_NO_RETRY");assertThat(result.providerError()).isEqualTo(error);assertThat(service(true,environment).view(19L).providerError()).isEqualTo(error);
        assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);verify(client,times(1)).createAdministrative(eq(19L),anyMap(),anyString());}
    @Test void everyPartialIdIsSavedWithoutClaimingSuccessOrAllowingAnotherRequest(){var service=service(true,environment);var c=confirmation(service);when(client.createAdministrative(eq(19L),anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("900001","900002"),false,false));
        var result=service.createReal(19L,"admin",c);assertThat(result.supplierOrderIds()).containsExactly("900001","900002");assertThat(result.realStatus()).isEqualTo("VERIFICATION_REQUIRED");assertThat(result.createdAt()).isNull();assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);}
    @Test void committedClaimPrecedesHttpAndConcurrentClicksProduceOneCreate()throws Exception{var service=service(true,environment);var c=confirmation(service);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);when(client.createAdministrative(eq(19L),anyMap(),anyString())).thenAnswer(inv->{entered.countDown();assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();return AliExpressOrderClient.Result.unknown();});var pool=Executors.newFixedThreadPool(2);
        try{var first=pool.submit(()->service.createReal(19L,"admin",c));assertThat(entered.await(15,TimeUnit.SECONDS)).isTrue();assertThat(service.view(19L).realStatus()).isEqualTo("VERIFICATION_REQUIRED");assertThatThrownBy(()->service.createReal(19L,"admin",c)).isInstanceOf(ResourceConflictException.class);release.countDown();first.get(15,TimeUnit.SECONDS);verify(client,times(1)).createAdministrative(eq(19L),anyMap(),anyString());}finally{release.countDown();pool.shutdownNow();}}
    @Test void existingUnknownIdentityTokenCanQueryItsStoredIdsWithoutMethodAttestation(){var service=service(true,environment);service.createReal(19L,"admin",confirmation(service));when(queryClient.queryAdministrative(19L,"900001","synthetic-token")).thenReturn(AliExpressSupplierSnapshot.classify("900001","PLACE_ORDER_SUCCESS",null));var result=sync(service).sync(19L);assertThat(result.supplierSync().parts()).singleElement().satisfies(p->{assertThat(p.verified()).isTrue();assertThat(p.state()).isEqualTo("AWAITING_MANUAL_PAYMENT");});assertThat(payments.findAll()).isEmpty();verifyNoInteractions(queryPermission);}
    @Test void queryCannotSwitchAKnownBuyerAfterCreation(){
        doAnswer(inv->{var base=inv.getArgument(0,AliExpressTestFixture.class);return new AliExpressTestReadiness.Check(base,"buyer-19","Owner account","KNOWN",Instant.now(),Instant.now().plusSeconds(120),List.of(),"IMPORTED_VARIANT");}).when(readiness).check(any(),anyString());
        var service=service(true,environment);service.createReal(19L,"admin",confirmation(service));doReturn(new AliExpressTokenService.BuyerIdentity("other-buyer",null)).when(tokens).buyerIdentity();
        assertThatThrownBy(()->sync(service).sync(19L)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(queryClient);
    }
    @Test void unknownIdentityAfterTokenRotationCanStillQueryOnlyPersistedOwnIds(){
        var service=service(true,environment);service.createReal(19L,"admin",confirmation(service));when(tokens.accessToken()).thenReturn("rotated-token");
        when(queryClient.queryAdministrative(19L,"900001","rotated-token")).thenReturn(AliExpressSupplierSnapshot.classify("900001","PLACE_ORDER_SUCCESS",null));
        assertThat(sync(service).sync(19L).supplierSync().parts()).allMatch(p->p.verified());verify(queryClient).queryAdministrative(19L,"900001","rotated-token");verify(client,times(1)).createAdministrative(eq(19L),anyMap(),anyString());
    }
    @Test void queryWithNoIdsDoesNotCreateOrProbeOtherOrders(){var service=service(true,environment);assertThatThrownBy(()->sync(service).sync(19L)).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(tokens,client,queryClient);}
    @Test void existingSimulationCannotAuthorizeNormalCustomerPurchase(){var service=service(true,environment);service.simulate(19L,"admin",false);service.simulate(19L,"admin",true);assertThat(normalSupplier.view(19L).canSend()).isFalse();assertThat(payments.findAll()).isEmpty();verifyNoInteractions(client,tokens);}
}

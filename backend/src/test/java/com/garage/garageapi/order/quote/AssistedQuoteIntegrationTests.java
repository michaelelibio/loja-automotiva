package com.garage.garageapi.order.quote;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.service.CheckoutProWebhookUpdater;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.integration.aliexpress.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aliexpress.orders.enabled=false","aliexpress.orders.contract-confirmed=false","app.account.email.mode=disabled"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class AssistedQuoteIntegrationTests {
    @Autowired MockMvc mvc; @Autowired OrderRepository orders; @Autowired PaymentRepository payments;
    @Autowired OrderFulfillmentRepository fulfillments; @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants; @Autowired AddressRepository addresses; @Autowired UserRepository users;
    @Autowired CheckoutProWebhookUpdater webhook;
    @MockitoBean CheckoutProGateway gateway;
    @MockitoBean AliExpressOrderClient supplier;
    @MockitoBean AliExpressTokenService tokens;
    User user;Address address;Product product;ProductVariant variant;
    static final JsonMapper JSON=JsonMapper.builder().build();
    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString();
        user=users.save(User.local("Cliente Teste","assisted-"+suffix+"@example.test","hash"));
        address=addresses.save(new Address(user,"Casa","Cliente Teste","89229040","Rua Original","10",null,"Centro","Joinville","SC",true));
        product=new Product("Produto","assisted-"+suffix,"Descricao","Descricao",new BigDecimal("50"),null,"Acessorios",10,null,true);
        product.linkSupplier("ALIEXPRESS","1005010661577657",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        variant=variants.saveAndFlush(new ProductVariant(product,"ALIEXPRESS","12000053109276547","1005010661577657","SKU","Azul",Map.of("14","Azul"),"14:771#Azul",new BigDecimal("25.39"),"BRL",null,null,null,null,null));
        when(gateway.createPreference(any())).thenAnswer(call->{var r=(CheckoutProGateway.PreferenceRequest)call.getArgument(0);
            assertThat(r.total()).isEqualByComparingTo("74.00");assertThat(r.expiresAt()).isNotNull();
            return new CheckoutProGateway.PreferenceResult("TEST-"+r.paymentId(),"garage_order_"+r.orderId()+"_payment_"+r.paymentId(),"https://sandbox.mercadopago.com/test");});
    }
    @AfterEach void noSupplierMutation() {verifyNoInteractions(supplier,tokens);}
    String customerBody(String key) {return JSON.writeValueAsString(Map.of("requestKey",key,"addressId",address.getId(),"shippingCode","ASSISTED_QUOTE",
        "items",List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",1)),
        "recipient",Map.of("cpf","52998224725","birthDate","29/02/2000","phone","47999999999")));}
    org.springframework.test.web.servlet.request.RequestPostProcessor customer() {return jwt().jwt(j->j.subject(user.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));}
    org.springframework.test.web.servlet.request.RequestPostProcessor admin() {return jwt().jwt(j->j.subject("admin-test")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    long create() throws Exception {return JSON.readTree(mvc.perform(post("/api/orders").with(customer()).contentType(MediaType.APPLICATION_JSON).content(customerBody(UUID.randomUUID().toString())))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.quote.state").value("WAITING")).andReturn().getResponse().getContentAsString()).path("id").asLong();}
    String confirmation(String sku,Instant until) {return JSON.writeValueAsString(Map.of("supplierTotalBrl",60,"expectedTotal",74,"validUntil",until.toString(),"evidenceReference","internal/quote-001","fullAddressAndTaxesChecked",true,
        "legs",List.of(Map.of("code","CAINIAO_STANDARD","name","AliExpress Standard Shipping","originCountry","CN","shippingBrl",24,"estimatedDays",25,"skuIds",List.of(sku)))));}
    void confirm(long id) throws Exception {mvc.perform(post("/api/admin/orders/"+id+"/quote").with(admin()).contentType(MediaType.APPLICATION_JSON).content(confirmation(variant.getSupplierVariantId(),Instant.now().plusSeconds(7200))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.quote.state").value("CONFIRMED")).andExpect(jsonPath("$.total").value(74));}
    void accept(long id) throws Exception {mvc.perform(post("/api/orders/"+id+"/quote/accept").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("revision",1,"expectedTotal",74))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.quote.state").value("ACCEPTED"));}
    void pay(long id) throws Exception {
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isCreated());
        var payment=payments.findAllByOrderIdOrderByCreatedAtDescIdDesc(id).get(0);
        webhook.apply("TEST-PAY-"+id,new CheckoutProGateway.PaymentResult("TEST-PAY-"+id,payment.getExternalReference(),PaymentStatus.PAID,"approved",new BigDecimal("74"),"BRL","credit_card","test",Instant.now()));
    }
    void recheck(long id,int cost,String state) throws Exception {mvc.perform(post("/api/admin/orders/"+id+"/quote/recheck").with(admin()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("supplierTotalBrl",cost,"sameAddressItemsAndMethods",true))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.quote.state").value(state));}

    @Test void fullAssistedSaleWithoutAnyRealChargeOrSupplierOrder() throws Exception {
        long id=create();
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isConflict());
        confirm(id);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isConflict());verifyNoInteractions(gateway);
        accept(id);accept(id);pay(id);
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
        recheck(id,60,"ACCEPTED");
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/orders/"+id+"/recipient-for-fulfillment").with(admin())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.cpf").value("52998224725"));
        String receipt=JSON.writeValueAsString(Map.of("supplierOrderIds",List.of("900000000000"+id)));
        for(int i=0;i<2;i++) mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-complete").with(admin()).contentType(MediaType.APPLICATION_JSON).content(receipt)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        assertThat(fulfillments.findByOrderId(id).orElseThrow().getSupplierOrderIds()).hasSize(1);
        verify(gateway,times(1)).createPreference(any());
    }
    @Test void changedCostPersistsHoldBlocksDispatchAndRecordsFullManualRefund() throws Exception {
        long id=create();confirm(id);accept(id);pay(id);recheck(id,61,"REFUND_REQUIRED");
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/orders/"+id+"/status").with(admin()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("status","PROCESSING")))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/orders/"+id+"/quote/refund-record").with(admin()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("reference","mp-refund/test-001"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED")).andExpect(jsonPath("$.quote.state").value("REFUNDED"));
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
        assertThat(orders.findById(id).orElseThrow().getTotal()).isEqualByComparingTo("74");
    }
    @Test void rejectsForgedTotalWrongSkuExpiryAndUnauthorizedAccess() throws Exception {
        long id=create();
        mvc.perform(post("/api/admin/orders/"+id+"/quote").with(customer()).contentType(MediaType.APPLICATION_JSON).content(confirmation(variant.getSupplierVariantId(),Instant.now().plusSeconds(1000)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/orders/"+id+"/quote").with(admin()).contentType(MediaType.APPLICATION_JSON).content(confirmation("9999",Instant.now().plusSeconds(1000)))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/orders/"+id+"/quote").with(admin()).contentType(MediaType.APPLICATION_JSON).content(confirmation(variant.getSupplierVariantId(),Instant.now().minusSeconds(1)))).andExpect(status().isConflict());
        confirm(id);
        mvc.perform(post("/api/orders/"+id+"/quote/accept").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("revision",1,"expectedTotal",0.01)))).andExpect(status().isConflict());
        var another=users.save(User.local("Outro Cliente","other-"+UUID.randomUUID()+"@example.test","hash"));
        mvc.perform(post("/api/orders/"+id+"/quote/accept").with(jwt().jwt(j->j.subject(another.getId().toString()))).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("revision",1,"expectedTotal",74)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/orders/"+id+"/recipient-for-fulfillment").with(customer())).andExpect(status().isForbidden());
        verifyNoInteractions(gateway);
    }
    @Test void idempotentRequestAndImmutableAddressAndCatalogSnapshots() throws Exception {
        String key=UUID.randomUUID().toString(),body=customerBody(key);
        long first=0;
        for(int i=0;i<2;i++) {long id=JSON.readTree(mvc.perform(post("/api/orders").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();if(i==0)first=id;else assertThat(id).isEqualTo(first);}
        address.update("Casa","Outro Cliente","89229040","Rua Alterada","50",null,"Centro","Joinville","SC");addresses.saveAndFlush(address);
        confirm(first);var order=orders.findById(first).orElseThrow();assertThat(order.getStreet()).isEqualTo("Rua Original");
        assertThat(order.getItems()).singleElement().satisfies(item->assertThat(item.getSupplierVariantId()).isEqualTo(variant.getSupplierVariantId()));
    }
    @Test void expiredAcceptedQuoteCannotReachPaymentGateway() throws Exception {
        long id=create();confirm(id);accept(id);var order=orders.findById(id).orElseThrow();var q=order.getAssistedQuote();
        order.updateAssistedQuote(new AssistedQuote(q.state(),q.revision(),q.confirmedAt(),Instant.now().minusSeconds(1),q.acceptedAt(),q.supplierTotalBrl(),q.evidenceReference(),q.confirmedBy(),null,null));orders.saveAndFlush(order);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isConflict());verifyNoInteractions(gateway);
    }


    @Test void multipleShipmentsCoverEachSkuExactlyOnce() throws Exception {
        var second=variants.saveAndFlush(new ProductVariant(product,"ALIEXPRESS","12000053109276548","1005010661577657","SKU2","Preto",Map.of("14","Preto"),"14:193#Preto",new BigDecimal("25.39"),"BRL",null,null,null,null,null));
        var body=(tools.jackson.databind.node.ObjectNode)JSON.readTree(customerBody(UUID.randomUUID().toString()));
        ((tools.jackson.databind.node.ArrayNode)body.path("items")).addObject().put("productId",product.getId()).put("variantId",second.getId()).put("quantity",1);
        long id=JSON.readTree(mvc.perform(post("/api/orders").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        var quote=(tools.jackson.databind.node.ObjectNode)JSON.readTree(confirmation(variant.getSupplierVariantId(),Instant.now().plusSeconds(3600)));
        quote.put("expectedTotal",134);quote.put("supplierTotalBrl",90);
        var legs=(tools.jackson.databind.node.ArrayNode)quote.path("legs");
        var extra=legs.addObject();extra.put("code","EXPRESS").put("name","Outra modalidade").put("originCountry","BR").put("shippingBrl",10).put("estimatedDays",15).putArray("skuIds").add(variant.getSupplierVariantId());
        mvc.perform(post("/api/admin/orders/"+id+"/quote").with(admin()).contentType(MediaType.APPLICATION_JSON).content(quote.toString())).andExpect(status().isConflict());
        extra.putArray("skuIds").add(second.getSupplierVariantId());
        mvc.perform(post("/api/admin/orders/"+id+"/quote").with(admin()).contentType(MediaType.APPLICATION_JSON).content(quote.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.shipping.legs.length()").value(2)).andExpect(jsonPath("$.total").value(134));
        var order=orders.findById(id).orElseThrow();assertThat(order.getShippingCost()).isEqualByComparingTo("34");assertThat(order.getShippingEstimatedDays()).isEqualTo(25);
        verifyNoInteractions(gateway);
    }
    @Test void approvalAfterQuoteValidityRequiresRefundInsteadOfDispatch() throws Exception {
        long id=create();confirm(id);accept(id);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("method","MERCADO_PAGO")))).andExpect(status().isCreated());
        var payment=payments.findAllByOrderIdOrderByCreatedAtDescIdDesc(id).get(0);
        var until=orders.findById(id).orElseThrow().getAssistedQuote().validUntil();
        webhook.apply("LATE-"+id,new CheckoutProGateway.PaymentResult("LATE-"+id,payment.getExternalReference(),PaymentStatus.PAID,"approved",new BigDecimal("74"),"BRL","credit_card","test",until.plusSeconds(1)));
        assertThat(orders.findById(id).orElseThrow().getAssistedQuote().state()).isEqualTo("REFUND_REQUIRED");
        mvc.perform(post("/api/admin/orders/"+id+"/quote/manual-claim").with(admin())).andExpect(status().isConflict());
    }
}

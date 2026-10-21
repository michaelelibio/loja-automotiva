package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.dto.*;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.recipient.AliExpressRecipientData;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.shipping.service.ShippingService;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aliexpress.orders.enabled=false","aliexpress.orders.contract-confirmed=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class AliExpressCheckoutRecipientIntegrationTests {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired OrderFulfillmentRepository fulfillments;
    @Autowired PaymentRepository payments;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductMediaRepository media;
    @Autowired AddressRepository addresses;
    @Autowired UserRepository users;
    @MockitoSpyBean ShippingService shipping;
    @MockitoBean AliExpressOrderClient client;
    @MockitoBean AliExpressTokenService tokens;
    @BeforeEach void clean() {
        reset(shipping,client,tokens);
        fulfillments.deleteAll();payments.deleteAll();orders.deleteAll();media.deleteAll();variants.deleteAll();products.deleteAll();addresses.deleteAll();users.deleteAll();
        // An isolated quote fixture tests recipient persistence without claiming real AliExpress freight support.
        doReturn(new ShippingProvider.Option("TEST","Frete simulado",BigDecimal.TEN,10)).when(shipping).select(anyString(),anyList(),any());
    }
    @Test void savesNormalizedDataAndAddressSnapshotWithoutReturningOrLoggingPersonalValues(CapturedOutput output) throws Exception {
        var fixture=fixture(true);
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body(fixture).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recipient").doesNotExist()).andExpect(jsonPath("$.recipientCpf").doesNotExist());
        var saved=orders.findAll().get(0);
        assertThat(saved.getRecipientCpf()).isEqualTo("52998224725");
        assertThat(saved.getRecipientBirthDate()).isEqualTo(LocalDate.of(2000,2,29));
        assertThat(saved.getRecipientPhone()).isEqualTo("47999999999");
        assertThat(saved.getStatus().name()).isEqualTo("PENDING_PAYMENT");assertThat(payments.count()).isZero();
        assertThat(AliExpressOrderPreparation.logisticsAddress(saved)).containsEntry("birth_date","29022000")
                .containsEntry("cpf","52998224725").containsEntry("phone_country","+55").containsEntry("mobile_no","47999999999");
        var address=fixture.address();address.update("Casa","Outro Destinatário","89229040","Rua Alterada","999",null,"Centro","Joinville","SC");addresses.saveAndFlush(address);
        assertThat(orders.findById(saved.getId()).orElseThrow().getStreet()).isEqualTo("Rua Original");
        assertThat(orders.findById(saved.getId()).orElseThrow().getRecipientName()).isEqualTo("Cliente Teste");
        for(String path:List.of("/api/orders","/api/orders/"+saved.getId())) {
            var response=mvc.perform(get(path).with(customer(fixture.user()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertPrivateValuesAbsent(response);
        }
        var admin=mvc.perform(get("/api/admin/orders/"+saved.getId()).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientData.complete").value(true)).andExpect(jsonPath("$.recipientData.pendingFields.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertPrivateValuesAbsent(admin);assertPrivateValuesAbsent(output.getAll());
        verifyNoInteractions(client,tokens);
    }
    @ParameterizedTest @ValueSource(strings={"missing","cpf","repeatedCpf","birthDate","futureBirthDate","isoBirthDate","phone","ddd","name","uf"})
    void invalidRequiredFieldsFailSafelyWithoutOrderPersistence(String failure,CapturedOutput output) throws Exception {
        var fixture=fixture(true);var body=body(fixture);var recipient=(ObjectNode)body.path("recipient");
        switch(failure) {
            case "missing" -> body.remove("recipient");
            case "cpf" -> recipient.put("cpf","529.982.247-24");
            case "repeatedCpf" -> recipient.put("cpf","111.111.111-11");
            case "birthDate" -> recipient.put("birthDate","31/02/2000");
            case "futureBirthDate" -> recipient.put("birthDate","01/01/2999");
            case "isoBirthDate" -> recipient.put("birthDate","2000-02-29");
            case "phone" -> recipient.put("phone","123");
            case "ddd" -> recipient.put("phone","(20) 99999-9999");
            case "name" -> {var a=fixture.address();a.update("Casa","Cliente","89229040","Rua Original","10",null,"Centro","Joinville","SC");addresses.saveAndFlush(a);}
            case "uf" -> {var a=fixture.address();a.update("Casa","Cliente Teste","89229040","Rua Original","10",null,"Centro","Joinville","XX");addresses.saveAndFlush(a);}
            default -> throw new AssertionError();
        }
        var response=mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").isNotEmpty()).andReturn().getResponse().getContentAsString();
        assertPrivateValuesAbsent(response);assertPrivateValuesAbsent(output.getAll());
        assertThat(orders.count()).isZero();verifyNoInteractions(client,tokens);
    }
    @Test void parserFailuresDoNotEchoPersonalValues(CapturedOutput output) throws Exception {
        var fixture=fixture(true);var body=body(fixture);((ObjectNode)body.path("recipient")).putArray("cpf").add("52998224725");
        var response=mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertPrivateValuesAbsent(response);assertPrivateValuesAbsent(output.getAll());
    }
    @Test void nonAliExpressCheckoutDoesNotRequireOrStoreAdditionalData() throws Exception {
        var fixture=fixture(false);var body=body(fixture);body.remove("recipient");
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isCreated());
        var saved=orders.findAll().get(0);assertThat(saved.getRecipientCpf()).isNull();assertThat(saved.getRecipientBirthDate()).isNull();assertThat(saved.getRecipientPhone()).isNull();
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body(fixture).toString())).andExpect(status().isCreated());
        assertThat(orders.findAll()).allSatisfy(o->assertThat(o.getRecipientCpf()).isNull());
    }
    @Test void adminStatusRequiresAdminAndAnotherCustomerCannotReadOrder() throws Exception {
        var fixture=fixture(true);
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body(fixture).toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body(fixture).toString())).andExpect(status().isCreated());
        long id=orders.findAll().get(0).getId();
        mvc.perform(get("/api/admin/orders/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders/"+id).with(customer(fixture.user()))).andExpect(status().isForbidden());
        var another=users.save(User.local("Outro Usuário","another@example.test","hash"));
        mvc.perform(get("/api/orders/"+id).with(customer(another))).andExpect(status().isNotFound());
        var tampered=body(fixture);tampered.put("addressId",addresses.save(new Address(another,"Casa","Outro Usuário","89229040","Outra Rua","20",null,"Centro","Joinville","SC",true)).getId());
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(tampered.toString())).andExpect(status().isNotFound());
    }
    @Test void historicalOrderRemainsUnmodifiedAndListsPendingFields() throws Exception {
        var fixture=fixture(true);
        var legacy=new com.garage.garageapi.order.entity.Order(fixture.user(),fixture.address(),new BigDecimal("50"),BigDecimal.TEN,Duration.ofHours(24));
        legacy.addItem(new com.garage.garageapi.order.entity.OrderItem(legacy,fixture.product(),fixture.variant(),1,new BigDecimal("50"),new BigDecimal("50")));orders.saveAndFlush(legacy);
        mvc.perform(get("/api/admin/orders/"+legacy.getId()).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientData.complete").value(false)).andExpect(jsonPath("$.recipientData.pendingFields.length()").value(3));
        assertThat(orders.findById(legacy.getId()).orElseThrow().getRecipientCpf()).isNull();
    }
    @Test void collectionDoesNotResolveFreightOrEnableRealOrders() throws Exception {
        var fixture=fixture(true);
        doThrow(new com.garage.garageapi.shipping.exception.InvalidShippingOptionException("Frete não confirmado."))
                .when(shipping).select(anyString(),anyList(),any());
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(body(fixture).toString())).andExpect(status().isBadRequest());
        assertThat(orders.count()).isZero();verifyNoInteractions(client,tokens);
    }
    @Test void catalogOnlyRequiresRecipientDataForAliExpressFulfillment() throws Exception {
        var fixture=fixture(true);
        mvc.perform(get("/api/products/"+fixture.product().getId())).andExpect(jsonPath("$.requiresAliExpressRecipientData").value(true));
    }
    @Test void realShippingSelectionRejectsForgedLocalFreightWithoutPersistingOrder() throws Exception {
        var fixture=fixture(true);
        doCallRealMethod().when(shipping).select(anyString(),anyList(),any());
        var request=body(fixture);request.put("shippingCode","STANDARD");request.put("shippingCost",0);
        mvc.perform(post("/api/orders").with(customer(fixture.user())).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value("ALIEXPRESS_FREIGHT_UNAVAILABLE"));
        assertThat(orders.count()).isZero();assertThat(payments.count()).isZero();verifyNoInteractions(client,tokens);
    }
    @Test void requestRepresentationsNeverExposeValues() {
        var request=new AliExpressRecipientRequest("52998224725","29/02/2000","47999999999");
        assertPrivateValuesAbsent(request.toString());assertPrivateValuesAbsent(new CreateOrderRequest(1L,List.of(),"TEST",request).toString());
        assertThat(AliExpressRecipientData.cpfValid("52998224725")).isTrue();
    }
    private void assertPrivateValuesAbsent(String text) {assertThat(text).doesNotContain("52998224725","529.982.247-25","29/02/2000","2000-02-29","47999999999","(47) 99999-9999");}
    private ObjectNode body(Fixture fixture) {
        var body=JSON.createObjectNode();body.put("addressId",fixture.address().getId());body.put("shippingCode","TEST");
        var item=body.putArray("items").addObject();item.put("productId",fixture.product().getId());item.put("quantity",1);
        if(fixture.variant()!=null) item.put("variantId",fixture.variant().getId());
        var recipient=body.putObject("recipient");recipient.put("cpf","529.982.247-25");recipient.put("birthDate","29/02/2000");recipient.put("phone","(47) 99999-9999");return body;
    }
    private Fixture fixture(boolean ali) {
        var user=users.save(User.local("Cliente Teste","recipient@example.test","hash"));
        var address=addresses.save(new Address(user,"Casa","Cliente Teste","89229040","Rua Original","10",null,"Centro","Joinville","SC",true));
        var product=new Product("Produto","recipient-product","Descrição","Descrição",new BigDecimal("50"),null,"Acessórios",10,null,true);
        ProductVariant variant=null;
        if(ali) {product.linkSupplier("ALIEXPRESS","1005010661577657",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);}
        products.saveAndFlush(product);
        if(ali) variant=variants.saveAndFlush(new ProductVariant(product,"ALIEXPRESS","12000053109276547","1005010661577657","TEST-SKU","Azul",Map.of("14","Azul"),"14:771#Azul",new BigDecimal("25.39"),"BRL",null,null,null,null,null));
        return new Fixture(user,address,product,variant);
    }
    private record Fixture(User user,Address address,Product product,ProductVariant variant) { }
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(User user) {return jwt().jwt(j->j.subject(user.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));}
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
}

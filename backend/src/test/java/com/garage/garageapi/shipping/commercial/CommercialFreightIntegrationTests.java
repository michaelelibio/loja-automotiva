package com.garage.garageapi.shipping.commercial;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.integration.aliexpress.*;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.garage.garageapi.shipping.commercial.CommercialFreightPolicy.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All monetary fixtures below are synthetic, not a proposed or enabled real freight table. */
@SpringBootTest(properties={"app.shipping.commercial.enabled=true","app.payment.creation-enabled=false","app.account.email.mode=disabled"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class CommercialFreightIntegrationTests {
    @Autowired MockMvc mvc;@Autowired CommercialShippingService service;@Autowired CommercialFreightTables tables;
    @Autowired UserRepository users;@Autowired AddressRepository addresses;@Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;@Autowired OrderRepository orders;
    @MockitoBean PostalStateResolver postal;@MockitoBean AliExpressDsFreightClient freight;
    @MockitoBean AliExpressTokenService tokens;@MockitoBean AliExpressOrderClient supplier;@MockitoBean CheckoutProGateway gateway;
    User user;Address address;Product product;ProductVariant variant;
    static final JsonMapper JSON=JsonMapper.builder().build();
    @BeforeEach void fixture(){
        user=users.save(User.local("Cliente Sintetico","uf-"+UUID.randomUUID()+"@example.test","hash"));
        address=addresses.save(new Address(user,"Teste","Cliente Sintetico","89229040","Rua Sintetica","10",null,"Centro","Joinville","SC",true));
        product=new Product("Produto Sintetico","uf-"+UUID.randomUUID(),"Descricao","Descricao",b("50"),null,"Acessorios",100,null,true);
        product.linkSupplier("ALIEXPRESS","1005010661577657",null,null,Instant.now());product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        variant=variant("12000053109276547");when(postal.resolve("89229040")).thenReturn("SC");
    }
    ProductVariant variant(String sku){var v=new ProductVariant(product,"ALIEXPRESS",sku,"1005010661577657","SKU","Azul",Map.of(),"14:771#Azul",b("25.39"),"BRL",null,null,null,null,null);v.setSupplierStockQuantity(100);return variants.saveAndFlush(v);}
    @AfterEach void noCommercialSideEffects(){verifyNoInteractions(freight,tokens,supplier,gateway);}
    static BigDecimal b(String n){return new BigDecimal(n);}
    Evidence evidence(){return new Evidence(Source.ALIEXPRESS_PORTAL,"synthetic/test",Instant.now().minusSeconds(60),Instant.now().plusSeconds(7200),true,true);}
    Profile profile(ProductVariant v){return new Profile(product.getId(),v.getId(),ParcelSize.SMALL,product.getSupplierProductId(),v.getSupplierVariantId(),"synthetic-store","CN",b("25.39"),b("1"),evidence());}
    Rate rate(String uf,Grouping grouping){return new Rate("synthetic-"+uf,uf,"Acessorios",ParcelSize.SMALL,"synthetic-store","CN","STANDARD","Standard",b("10"),b("20"),b("5"),100,b("10000"),2,grouping,true,20,evidence());}
    Document doc(List<Profile> profiles,List<Rate> rates,String margin){var fee=new Evidence(Source.PAYMENT_PROVIDER_PLAN,"synthetic/fee-plan",Instant.now().minusSeconds(60),Instant.now().plusSeconds(7200),true,true);return new Document(b("10"),b("5"),b("1"),b(margin),b("0"),fee,profiles,rates);}
    CommercialShippingService.View activate(Document doc){return service.publish(service.draft(doc).version());}
    CommercialShippingService.View activate(){return activate(doc(List.of(profile(variant)),List.of(rate("SC",Grouping.PER_SKU)),"20"));}
    ShippingProvider.Request request(int qty){return new ShippingProvider.Request("89229040",List.of(item(variant,qty)));}
    ShippingProvider.Item item(ProductVariant v,int qty){return new ShippingProvider.Item(product.getId(),v.getId(),qty,product.getPrice(),FulfillmentType.DROPSHIPPING,"ALIEXPRESS",v.getSupplierVariantId());}
    org.springframework.test.web.servlet.request.RequestPostProcessor customer(){return jwt().jwt(j->j.subject(user.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));}
    org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    Map<String,Object> consent(){var a=new LinkedHashMap<String,Object>();a.put("recipientName",address.getRecipientName());a.put("zipCode",address.getZipCode());a.put("street",address.getStreet());a.put("number",address.getNumber());a.put("complement",null);a.put("neighborhood",address.getNeighborhood());a.put("city",address.getCity());a.put("state",address.getState());return a;}
    Map<String,Object> body(String code,String key,int total){return new LinkedHashMap<>(Map.of("addressId",address.getId(),"items",List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",1)),"shippingCode",code,"requestKey",key,"expectedTotal",total,"expectedAddress",consent(),"recipient",Map.of("cpf","52998224725","birthDate","29/02/2000","phone","47999999999")));}
    long create(String code,String key) throws Exception {return JSON.readTree(mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(body(code,key,70)))).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(70)).andExpect(jsonPath("$.quote").isEmpty()).andReturn().getResponse().getContentAsString()).path("id").asLong();}

    @Test void guestQuoteImmediateOrderSnapshotIdempotencyAndBlockedPayment() throws Exception {
        var table=activate();var quoteBody=JSON.writeValueAsString(Map.of("zipCode","89229040","items",List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",1))));
        var tree=JSON.readTree(mvc.perform(post("/api/shipping/quote").contentType("application/json").content(quoteBody)).andExpect(status().isOk()).andExpect(jsonPath("$.options[0].price").value(20)).andExpect(jsonPath("$.options[0].supplierCost").doesNotExist()).andReturn().getResponse().getContentAsString());
        String code=tree.path("options").get(0).path("code").asText();assertThat(code).hasSize(45);
        String key=UUID.randomUUID().toString();long id=create(code,key);assertThat(create(code,key)).isEqualTo(id);
        var order=orders.findById(id).orElseThrow();assertThat(order.getCommercialShipping().tableVersion()).isEqualTo(table.version());
        assertThat(order.getCommercialShipping().netProfitBrl()).isEqualByComparingTo("19.97");order.requireCommercialContextUnchanged();service.revalidate(order);
        mvc.perform(post("/api/orders/"+id+"/payments").with(customer()).contentType("application/json").content("{\"method\":\"MERCADO_PAGO\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.checkoutUrl").doesNotExist());
    }
    @Test void missingTariffOffersAssistedOrderWithoutInventingShipping() throws Exception {
        assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);
        var body=body("ASSISTED_QUOTE",UUID.randomUUID().toString(),70);body.remove("expectedTotal");body.remove("expectedAddress");
        mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(body))).andExpect(status().isCreated()).andExpect(jsonPath("$.quote.state").value("WAITING"));
    }
    @Test void missingStateAndLowMarginAreRejected(){activate(doc(List.of(profile(variant)),List.of(rate("SP",Grouping.PER_SKU)),"20"));assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);activate(doc(List.of(profile(variant)),List.of(rate("SC",Grouping.PER_SKU)),"90"));assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);}
    @Test void duplicateLinesAndMultipleVolumesArePackedExactlyOnce(){activate();assertThat(service.evaluate(new ShippingProvider.Request("89229040",List.of(item(variant,1),item(variant,1)))).option().price()).isEqualByComparingTo("20");assertThat(service.evaluate(request(3)).option().price()).isEqualByComparingTo("40");}
    @Test void cannotExtrapolateUnitTaxesOutsideVerifiedDomain(){var r=rate("SC",Grouping.PER_SKU);var limited=new Rate(r.key(),r.state(),r.category(),r.size(),r.supplierStoreId(),r.originCountry(),r.logisticsCode(),r.logisticsName(),r.supplierFreightPerParcelBrl(),r.customerFreightPerParcelBrl(),r.taxesPerUnitBrl(),1,b("100"),r.unitsPerParcel(),r.grouping(),true,r.estimatedDays(),r.evidence());activate(doc(List.of(profile(variant)),List.of(limited),"20"));assertThat(service.evaluate(request(1)).option().price()).isEqualByComparingTo("20");assertThatThrownBy(()->service.evaluate(request(2))).isInstanceOf(ResourceConflictException.class);}
    @Test void consolidationMustBeExplicitAndSeparateSkusUseSeparateCharges(){var other=variant("999");activate(doc(List.of(profile(variant),profile(other)),List.of(rate("SC",Grouping.CONSOLIDATED)),"20"));var request=new ShippingProvider.Request("89229040",List.of(item(variant,1),item(other,1)));assertThat(service.evaluate(request).option().price()).isEqualByComparingTo("20");activate(doc(List.of(profile(variant),profile(other)),List.of(rate("SC",Grouping.PER_SKU)),"20"));assertThat(service.evaluate(request).option().price()).isEqualByComparingTo("40");}
    @ParameterizedTest @ValueSource(strings={"AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"})
    void all27StatesHaveExactSelectionWithoutDefaultPrices(String uf){when(postal.resolve("89229040")).thenReturn(uf);activate(doc(List.of(profile(variant)),List.of(rate(uf,Grouping.PER_SKU)),"20"));assertThat(service.evaluate(request(1)).snapshot().state()).isEqualTo(uf);}
    @Test void changedPriceVersionAddressAndExpectedTotalRequireNewConsent() throws Exception {
        activate();String code=service.evaluate(request(1)).option().code();
        mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(body(code,UUID.randomUUID().toString(),69)))).andExpect(status().isConflict());
        var changed=body(code,UUID.randomUUID().toString(),70);changed.put("expectedAddress",Map.of("street","changed"));mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(changed))).andExpect(status().isConflict());
        activate();mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(body(code,UUID.randomUUID().toString(),70)))).andExpect(status().isConflict());
        String current=service.evaluate(request(1)).option().code();ReflectionTestUtils.setField(product,"price",b("60"));products.saveAndFlush(product);
        mvc.perform(post("/api/orders").with(customer()).contentType("application/json").content(JSON.writeValueAsString(body(current,UUID.randomUUID().toString(),70)))).andExpect(status().isConflict());
    }
    @Test void acceptedOrderDoesNotChangeAndOldVersionIsBlockedAtRevalidation() throws Exception {activate();long id=create(service.evaluate(request(1)).option().code(),UUID.randomUUID().toString());var order=orders.findById(id).orElseThrow();activate();assertThatThrownBy(()->service.revalidate(order)).isInstanceOf(ResourceConflictException.class);assertThat(order.getTotal()).isEqualByComparingTo("70");ReflectionTestUtils.setField(order,"street","changed");assertThatThrownBy(order::requireCommercialContextUnchanged).isInstanceOf(ResourceConflictException.class);}
    @Test void expiredEvidenceAndRetiredTableFailClosed(){var table=activate();service.retire(table.version());assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);var bad=new Evidence(Source.ALIEXPRESS_PORTAL,"synthetic/expired",Instant.now().minusSeconds(100),Instant.now().minusSeconds(10),true,true);var r=rate("SC",Grouping.PER_SKU);var expired=new Rate(r.key(),r.state(),r.category(),r.size(),r.supplierStoreId(),r.originCountry(),r.logisticsCode(),r.logisticsName(),r.supplierFreightPerParcelBrl(),r.customerFreightPerParcelBrl(),r.taxesPerUnitBrl(),r.maximumVerifiedUnits(),r.maximumVerifiedSupplierBaseBrl(),r.unitsPerParcel(),r.grouping(),true,r.estimatedDays(),bad);var draft=service.draft(doc(List.of(profile(variant)),List.of(expired),"20"));assertThatThrownBy(()->service.publish(draft.version())).isInstanceOf(ResourceConflictException.class);}
    @Test void diagnosticsCannotAuthorizeTariffAndAdminEndpointsAreProtected() throws Exception {var p=profile(variant);var bad=new Profile(p.productId(),p.variantId(),p.size(),p.supplierProductId(),p.supplierSkuId(),p.supplierStoreId(),p.originCountry(),p.supplierProductUnitBrl(),p.otherSupplierUnitBrl(),new Evidence(Source.DIAGNOSTIC_ONLY,"synthetic/major-match",Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),false,false));var draft=service.draft(doc(List.of(bad),List.of(rate("SC",Grouping.PER_SKU)),"20"));assertThatThrownBy(()->service.publish(draft.version())).isInstanceOf(ResourceConflictException.class);mvc.perform(get("/api/admin/shipping-tables")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/shipping-tables").with(customer())).andExpect(status().isForbidden());mvc.perform(get("/api/admin/shipping-tables").with(admin())).andExpect(status().isOk());}
    Document bulk(Document base,List<ReviewRow> rows){return new Document(base.costReservePercent(),base.paymentFeePercent(),base.paymentFixedFeeBrl(),base.minimumNetMarginPercent(),base.minimumNetProfitBrl(),base.paymentFeeEvidence(),base.profiles(),base.rates(),rows);}
    Rate blank(String uf,ParcelSize size){return new Rate("planned-"+uf+"-"+size,uf,"Acessorios",size,"synthetic-store","CN","","",null,null,null,0,null,0,Grouping.PER_SKU,false,0,null);}
    @Test void national81UnknownPricesPersistAsInactiveDraftAndCannotPublish() throws Exception {
        var rows=new ArrayList<ReviewRow>();for(var uf:AliExpressDsFreightClient.BRAZILIAN_PROVINCES.keySet())for(var size:ParcelSize.values())rows.add(new ReviewRow(blank(uf,size),false));
        var empty=new Document(null,null,null,null,null,null,List.of(),List.of(),rows);
        var result=mvc.perform(post("/api/admin/shipping-tables/bulk").with(admin()).contentType("application/json").content(JSON.writeValueAsString(empty))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.document.rates.length()").value(0)).andExpect(jsonPath("$.document.reviewRows.length()").value(81)).andReturn();
        long id=JSON.readTree(result.getResponse().getContentAsString()).path("version").asLong();
        assertThatThrownBy(()->service.publish(id)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);
        mvc.perform(post("/api/admin/shipping-tables/bulk").with(customer()).contentType("application/json").content(JSON.writeValueAsString(empty))).andExpect(status().isForbidden());
    }
    @Test void bulkOnlyApprovedRowsReachCheckoutAndUpdatesRequirePublication(){
        var base=doc(List.of(profile(variant)),List.of(rate("RJ",Grouping.PER_SKU)),"20");
        var draft=service.bulkDraft(bulk(base,List.of(new ReviewRow(rate("SC",Grouping.PER_SKU),true),new ReviewRow(blank("SP",ParcelSize.SMALL),false))));
        assertThat(draft.document().rates()).extracting(Rate::state).containsExactly("SC");service.publish(draft.version());
        assertThat(service.evaluate(request(1)).option().price()).isEqualByComparingTo("20");when(postal.resolve("89229040")).thenReturn("SP");
        assertThatThrownBy(()->service.evaluate(request(1))).isInstanceOf(ResourceConflictException.class);when(postal.resolve("89229040")).thenReturn("SC");
        var next=service.bulkDraft(bulk(base,List.of(new ReviewRow(blank("SC",ParcelSize.SMALL),false))));
        assertThat(next.enabled()).isFalse();assertThat(service.evaluate(request(1)).option().price()).isEqualByComparingTo("20");
        assertThatThrownBy(()->service.publish(next.version())).isInstanceOf(ResourceConflictException.class);
    }
    @Test void bulkRejectsIncompleteApprovalDuplicateScopesAndAmbiguousEvidence(){
        var base=doc(List.of(profile(variant)),List.of(rate("SC",Grouping.PER_SKU)),"20");
        assertThatThrownBy(()->service.bulkDraft(bulk(base,List.of(new ReviewRow(blank("SC",ParcelSize.SMALL),true))))).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.bulkDraft(bulk(base,List.of(new ReviewRow(blank("SC",ParcelSize.SMALL),false),new ReviewRow(blank("SC",ParcelSize.SMALL),false))))).isInstanceOf(ResourceConflictException.class);
        var r=rate("SC",Grouping.PER_SKU);var ambiguous=new Rate(r.key(),r.state(),r.category(),r.size(),r.supplierStoreId(),r.originCountry(),r.logisticsCode(),r.logisticsName(),b("24"),b("30"),r.taxesPerUnitBrl(),r.maximumVerifiedUnits(),r.maximumVerifiedSupplierBaseBrl(),r.unitsPerParcel(),r.grouping(),true,r.estimatedDays(),new Evidence(Source.DIAGNOSTIC_ONLY,"synthetic/ambiguous",Instant.now().minusSeconds(60),Instant.now().plusSeconds(60),false,false));
        assertThatThrownBy(()->service.bulkDraft(bulk(base,List.of(new ReviewRow(ambiguous,true))))).isInstanceOf(ResourceConflictException.class);
    }
}

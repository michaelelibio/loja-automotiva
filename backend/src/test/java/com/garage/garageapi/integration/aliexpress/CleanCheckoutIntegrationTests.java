package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.dto.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.order.service.OrderService;
import com.garage.garageapi.payment.entity.*;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.service.CheckoutProWebhookUpdater;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All provider adapters are mocked; no real payment or supplier request can leave this test. */
@SpringBootTest(properties={"aliexpress.orders.enabled=true","aliexpress.orders.contract-confirmed=true",
        "app.payment.creation-enabled=false","app.delivery.verified-evidence=not-required"})
@ActiveProfiles("clean-checkout")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class CleanCheckoutIntegrationTests {
    @Autowired OrderService checkout;
    @Autowired AliExpressOrderService supplier;
    @Autowired CheckoutProWebhookUpdater webhook;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired OrderFulfillmentRepository fulfillments;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductMediaRepository media;
    @Autowired AddressRepository addresses;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @MockitoBean AliExpressOrderClient client;
    @MockitoBean AliExpressTokenService tokens;
    @MockitoBean AliExpressPlaceOrderPermission permission;
    @MockitoBean AliExpressProductClient productClient;
    @MockitoBean CheckoutProGateway mercadoPago;
    @MockitoBean ShippingProvider freight;
    User user; Address address; Product product; ProductVariant variant;
    static final BigDecimal PRICE = new BigDecimal("50.00");

    @BeforeEach void prepare() {
        fulfillments.deleteAll(); payments.deleteAll(); orders.deleteAll(); media.deleteAll(); variants.deleteAll();
        products.deleteAll(); addresses.deleteAll(); users.deleteAll();
        reset(client, tokens, permission, freight,productClient,mercadoPago);
        user = users.save(User.local("Cliente Teste", "clean@example.test", "synthetic-hash"));
        address = addresses.save(new Address(user,"Casa","Cliente Teste","89229040","Rua Teste","10",null,"Centro","Joinville","SC",true));
        product = new Product("Produto","clean-product","Descrição","Descrição",PRICE,null,"Acessórios",0,null,true);
        product.linkSupplier("ALIEXPRESS","1005010661577657",null,null,Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING); products.saveAndFlush(product);
        variant = new ProductVariant(product,"ALIEXPRESS","12000053109276547","1005010661577657","TEST-SKU","Azul",
                Map.of("14","Azul"),"14:771#Azul",new BigDecimal("25.39"),"BRL",null,null,null,null,null);
        variant.setSupplierStockQuantity(5); variants.saveAndFlush(variant);
        when(permission.verified()).thenReturn(true);
        when(tokens.accessToken()).thenReturn("synthetic-token");
        when(tokens.dispatchAccess()).thenReturn(new AliExpressTokenService.DispatchAccess("synthetic-token",AliExpressOAuthStateService.hash("synthetic-buyer")));
        when(productClient.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(JsonMapper.builder().build().readTree(
                "{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"12000053109276547\",\"sku_attr\":\"14:771#Azul\",\"sku_available_stock\":5}]}"));
        when(client.create(anyMap(),anyString())).thenReturn(new AliExpressOrderClient.Result(List.of("900000000000001"),false));
    }
    private Jwt customer() { return Jwt.withTokenValue("synthetic").header("alg","none").subject(user.getId().toString()).build(); }
    private CreateOrderRequest request(String key) {
        return new CreateOrderRequest(address.getId(),List.of(new CreateOrderItemRequest(product.getId(),variant.getId(),1)),
                "CATALOG_FREE",new AliExpressRecipientRequest("529.982.247-25","29/02/2000","(47) 99999-9999"),key,PRICE,
                new CreateOrderRequest.AddressConsent(address.getRecipientName(),address.getZipCode(),address.getStreet(),address.getNumber(),
                        address.getComplement(),address.getNeighborhood(),address.getCity(),address.getState()));
    }
    private void propertyless(String key) {
        variant.updateSupplierData("ALIEXPRESS",variant.getSupplierVariantId(),product.getSupplierProductId(),null,
                "Único",Map.of(),key,new BigDecimal("90.00"),"BRL",null,null,null,null,null);
        variants.saveAndFlush(variant);
    }
    @Test void confirmedSingleSkuCanCheckoutWithoutProviderCallsOrLocalStockReservation() {
        propertyless("");
        var created = checkout.create(customer(),request(UUID.randomUUID().toString()));
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orders.count()).isEqualTo(1); assertThat(payments.count()).isZero();
        assertThat(variants.findById(variant.getId()).orElseThrow().getSupplierStockQuantity()).isEqualTo(5);
        verifyNoInteractions(client,tokens,mercadoPago,freight);
    }
    @Test void missingLegacySelectionIsUnavailableInCatalogEligibilityAndCheckout() throws Exception {
        propertyless(null);
        mvc.perform(get("/api/products/"+product.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableForSale").value(false)).andExpect(jsonPath("$.variants[0].availableForSale").value(false));
        assertEligibilityRejected(List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",1)));
        assertThatThrownBy(() -> checkout.create(customer(),request(UUID.randomUUID().toString()))).hasMessageContaining("indisponível");
        assertThat(orders.count()).isZero(); verifyNoInteractions(client,tokens,mercadoPago,freight);
    }
    @Test void eligibilityRejectsAggregatedStockExhaustionAndForeignOrDeletedVariants() throws Exception {
        var line = Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",3);
        assertEligibilityRejected(List.of(line,line));
        var other = products.saveAndFlush(new Product("Outro","other-sku",null,null,PRICE,null,"Acessórios",0,null,true));
        assertEligibilityRejected(List.of(Map.of("productId",other.getId(),"variantId",variant.getId(),"quantity",1)));
        variants.delete(variant); variants.flush();
        mvc.perform(post("/api/orders/delivery-eligibility").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content(eligibilityBody(List.of(line)))).andExpect(status().isNotFound());
        assertThat(orders.count()).isZero(); verifyNoInteractions(client,tokens,mercadoPago,freight);
    }
    private String eligibilityBody(List<?> items) {
        return JsonMapper.builder().build().writeValueAsString(Map.of("addressId",address.getId(),"cart",Map.of("zipCode",address.getZipCode(),"items",items)));
    }
    private void assertEligibilityRejected(List<?> items) throws Exception {
        mvc.perform(post("/api/orders/delivery-eligibility").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content(eligibilityBody(items))).andExpect(status().isConflict());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void completeCheckoutPaymentAndSupplierLinkAreIdempotentAndVisibleInAdmin(boolean propertylessSku) throws Exception {
        if (propertylessSku) {
            propertyless("");
            when(productClient.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(JsonMapper.builder().build().readTree(
                    "{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"12000053109276547\",\"sku_price\":\"90.00\",\"currency_code\":\"BRL\",\"sku_available_stock\":5}]}"));
        }
        var request = request(UUID.randomUUID().toString());
        var created = checkout.create(customer(), request);
        assertThat(checkout.create(customer(), request).id()).isEqualTo(created.id());
        assertThat(orders.count()).isEqualTo(1);
        var order = orders.findById(created.id()).orElseThrow();
        assertThat(order.getShippingLegs()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotal()).isEqualByComparingTo(PRICE); assertThat(order.getShippingCost()).isZero();
        assertThat(payments.count()).isZero();
        assertThatThrownBy(() -> supplier.send(created.id())).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        verifyNoInteractions(tokens,client,freight);

        var payment = payments.saveAndFlush(new Payment(order,PaymentMethod.MERCADO_PAGO));
        String ref = "garage_order_"+order.getId()+"_payment_"+payment.getId();
        payment.applyCheckoutPreference("synthetic-preference",ref,"https://example.test/checkout"); payments.saveAndFlush(payment);
        var approved = new CheckoutProGateway.PaymentResult("synthetic-paid",ref,PaymentStatus.PAID,"approved",PRICE,"BRL","pix","pix",Instant.now(),true);
        when(mercadoPago.findPayment("synthetic-paid")).thenReturn(approved);
        webhook.apply("synthetic-paid",approved); webhook.apply("synthetic-paid",approved);
        var verifiedPayment=payments.findById(payment.getId()).orElseThrow();
        assertThat(verifiedPayment.getProviderLiveMode()).isTrue();assertThat(verifiedPayment.getProviderVerifiedAt()).isNotNull();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        var linked = supplier.send(order.getId());
        assertThat(linked.supplierOrderIds()).containsExactly("900000000000001");
        assertThat(linked.supplierStatus()).isEqualTo("CREATED_UNPAID_UNVERIFIED");
        supplier.send(order.getId());
        verify(client,times(1)).create(argThat(p -> p.keySet().equals(Set.of("out_order_id","ds_extend_request","logistics_address","product_items"))
                && ((List<?>)p.get("product_items")).stream().allMatch(line -> !((Map<?,?>)line).containsKey("logistics_service_name")
                    && (!propertylessSku || !((Map<?,?>)line).containsKey("sku_attr")))),eq("synthetic-token"));
        verifyNoInteractions(freight);
        mvc.perform(get("/api/admin/orders/"+order.getId()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(get("/api/orders/"+order.getId()).with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(50));
    }
    @Test void activatedProductCanBePurchasedWithoutEvidenceForTheSelectedAddress() {
        address.update("Casa","Cliente Teste","89229040","Rua Teste","11",null,"Centro","Joinville","SC"); addresses.saveAndFlush(address);
        var created=checkout.create(customer(),request(UUID.randomUUID().toString()));
        assertThat(created.shippingCost()).isZero();assertThat(created.total()).isEqualByComparingTo(PRICE);
        verifyNoInteractions(client,tokens,freight);
    }
    @Test void productionFreightRoutesAreAbsentAndAdminActivationControlsCatalog() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/admin/shipping-tables").with(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/shipping/quote").with(admin).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        product.setActive(false);products.saveAndFlush(product);
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        assertThatThrownBy(()->checkout.create(customer(),request(UUID.randomUUID().toString()))).isInstanceOf(com.garage.garageapi.favorite.exception.InactiveProductException.class);
        product.setActive(true);products.saveAndFlush(product);
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        verifyNoInteractions(client,tokens,freight);
    }
    @Test void changedTotalAddressConsentOrLegacyDeliveryCodeCannotCreateOrder() {
        var base = request(UUID.randomUUID().toString());
        var changedAddress = new CreateOrderRequest.AddressConsent("Cliente Teste",address.getZipCode(),"Outra Rua","10",null,"Centro","Joinville","SC");
        for (var changed : List.of(
                new CreateOrderRequest(base.addressId(),base.items(),base.shippingCode(),base.recipient(),base.requestKey(),PRICE.add(BigDecimal.ONE),base.expectedAddress()),
                new CreateOrderRequest(base.addressId(),base.items(),base.shippingCode(),base.recipient(),base.requestKey(),PRICE,changedAddress),
                new CreateOrderRequest(base.addressId(),base.items(),"ASSISTED_QUOTE",base.recipient(),base.requestKey(),PRICE,base.expectedAddress()),
                new CreateOrderRequest(base.addressId(),base.items(),"U_FORGED",base.recipient(),base.requestKey(),PRICE,base.expectedAddress()))) {
            assertThatThrownBy(() -> checkout.create(customer(),changed)).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);
        }
        assertThat(orders.count()).isZero(); verifyNoInteractions(client,tokens,freight);
    }
    @Test void unavailableVariantStockStillBlocksPurchase() {
        variant.setSupplierStockQuantity(0); variants.saveAndFlush(variant);
        assertThatThrownBy(() -> checkout.create(customer(),request(UUID.randomUUID().toString()))).hasMessageContaining("estoque");
        assertThat(orders.count()).isZero(); verifyNoInteractions(client,tokens,freight);
    }
    @Test void eligibilityChecksOwnedAddressAndExactQuantitiesWithoutFreightProvider() throws Exception {
        var cart = Map.of("zipCode",address.getZipCode(),"items",List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",1)));
        String body = JsonMapper.builder().build().writeValueAsString(Map.of("addressId",address.getId(),"cart",cart));
        mvc.perform(post("/api/orders/delivery-eligibility").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].code").value("CATALOG_FREE")).andExpect(jsonPath("$.options[0].price").value(0));
        var another = users.save(User.local("Outro Cliente","other-clean@example.test","synthetic-hash"));
        mvc.perform(post("/api/orders/delivery-eligibility").with(jwt().jwt(j -> j.subject(another.getId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
        var changedCart = Map.of("zipCode",address.getZipCode(),"items",List.of(Map.of("productId",product.getId(),"variantId",variant.getId(),"quantity",2)));
        mvc.perform(post("/api/orders/delivery-eligibility").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content(JsonMapper.builder().build().writeValueAsString(Map.of("addressId",address.getId(),"cart",changedCart))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options[0].price").value(0));
        verifyNoInteractions(client,tokens,freight);
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void operationMigrationIsAdditiveIdempotentAndDoesNotInventProductionEvidence() throws Exception {
        var resource=new org.springframework.core.io.ClassPathResource("db/migration/V20261030_03__payment_production_and_supplier_sync.sql");
        String sql=resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for(int run=0;run<2;run++) for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        assertThat(jdbc.queryForObject("select count(*) from payments where provider_live_mode = true",Long.class)).isZero();
        assertThat(products.count()).isEqualTo(1);
    }
}

package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Endpoint -> real Spring services/gateway -> real parser, with only HTTP/OAuth simulated. */
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:garage_freight_http_local;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.products.enabled=false",
        "aliexpress.app-key=synthetic-test-app", "aliexpress.app-secret=synthetic-test-secret",
        "aliexpress.orders.enabled=false", "aliexpress.orders.contract-confirmed=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(AliExpressShippingHttpIntegrationTests.HttpConfig.class)
@Transactional
class AliExpressShippingHttpIntegrationTests {
    static final String PID="1005010661577657",SKU="12000053109276547",TOKEN="synthetic-test-oauth";
    // Full synthetic BR address is a local fixture, not a claim of provider address coverage.
    static final Map<String,String> ADDRESS=Map.of("country","BR","zip","89229030","state","SC",
            "city","Joinville","street","Rua de Teste","number","10","neighborhood","Centro");
    @Autowired MockMvc mvc;
    @Autowired HttpFixture http;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    @MockitoBean AliExpressTokenService tokens;
    @MockitoBean AliExpressOrderClient supplierOrders;
    @MockitoBean CheckoutProGateway charges;
    @MockitoSpyBean AliExpressDsFreightGateway gateway;
    final AtomicReference<AliExpressFreightGateway.Evaluation> evaluation=new AtomicReference<>();
    final AtomicReference<JsonNode> sentDto=new AtomicReference<>();
    final AtomicInteger httpCalls=new AtomicInteger();
    Product product;
    ProductVariant variant;

    record HttpFixture(MockRestServiceServer server,AliExpressDsFreightClient client) {}
    @TestConfiguration(proxyBeanMethods=false)
    static class HttpConfig {
        @Bean HttpFixture localFreightHttp() {
            var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
            var server=MockRestServiceServer.bindTo(builder).build();
            return new HttpFixture(server,new AliExpressDsFreightClient(builder.build(),"synthetic-test-app",
                    "synthetic-test-secret",Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"),ZoneOffset.UTC)));
        }
        @Bean @Primary AliExpressDsFreightClient localFreightClient(HttpFixture http) { return http.client(); }
    }

    @BeforeEach void fixture() throws Exception {
        try(var connection=dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:garage_freight_http_local");
        }
        http.server().reset();evaluation.set(null);sentDto.set(null);httpCalls.set(0);
        when(tokens.existingAccessToken()).thenReturn(TOKEN);
        doAnswer(call->{var result=(AliExpressFreightGateway.Evaluation)call.callRealMethod();evaluation.set(result);return result;})
                .when(gateway).calculate(any());
        assertThat(context.getBean(AliExpressFreightGateway.class)).isSameAs(gateway);
        assertThat(ReflectionTestUtils.getField(gateway,"client")).isSameAs(http.client());
        assertThat(ReflectionTestUtils.getField(context.getBean(AliExpressFreightDiagnosticClient.class),"client")).isSameAs(http.client());
        product=new Product("Acessorio automotivo de teste","ae-http-local",null,null,new BigDecimal("59.90"),
                null,"Acessorios",0,null,true);
        product.linkSupplier("ALIEXPRESS",PID,new BigDecimal("25.39"),null,Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);products.saveAndFlush(product);
        variant=new ProductVariant(product,"ALIEXPRESS",SKU,PID,"AE-HTTP-SKU","Variante teste",Map.of("14","Black"),
                "14:771#Black",new BigDecimal("25.39"),"BRL",null,null,null,null,null);
        variant.setSupplierStockQuantity(10);variants.saveAndFlush(variant);
        assertThat(orders.count()).isZero();assertThat(payments.count()).isZero();
    }
    @AfterEach void noFinancialEffects() {
        http.server().verify();assertThat(orders.count()).isZero();assertThat(payments.count()).isZero();
        verifyNoInteractions(supplierOrders,charges);verify(tokens,never()).accessToken();
    }
    void responds(String body) {
        http.server().expect(requestTo("https://api-sg.aliexpress.com/sync"))
                .andExpect(method(HttpMethod.POST)).andExpect(request->{
                    httpCalls.incrementAndGet();Map<String,String> form=new TreeMap<>();
                    for(String part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                        var pair=part.split("=",2);form.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
                    }
                    assertThat(form).containsEntry("method","aliexpress.ds.freight.query").containsEntry("session",TOKEN)
                            .containsEntry("sign_method","sha256");
                    assertThat(form).doesNotContainKeys("access_token","app_secret");
                    JsonNode dto=JsonMapper.builder().build().readTree(form.get("queryDeliveryReq"));sentDto.set(dto);
                    assertThat(dto.size()).isEqualTo(7);assertThat(dto.path("productId").asText()).isEqualTo(PID);
                    assertThat(dto.path("selectedSkuId").asText()).isEqualTo(SKU);assertThat(dto.path("quantity").asInt()).isEqualTo(2);
                    assertThat(dto.path("shipToCountry").asText()).isEqualTo("BR");
                    assertThat(dto.toString()).doesNotContain(ADDRESS.get("zip"),ADDRESS.get("street"),"province","cityCode","FORGED");
                }).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    static String success(String cents,String origin) {
        return "{\"aliexpress_ds_freight_query_response\":{\"result\":{\"code\":200,\"success\":true,\"delivery_options\":[{"
                +"\"code\":\"CAINIAO_STANDARD\",\"company\":\"AliExpress Standard\",\"shipping_fee_currency\":\"BRL\","
                +"\"shipping_fee_cent\":\""+cents+"\",\"free_shipping\":false,\"ship_from_country\":\""+origin+"\","
                +"\"min_delivery_days\":5,\"max_delivery_days\":12}]}}}";
    }
    ResultActions quote(Long variantId) throws Exception {
        // Existing public contract only receives CEP, local item IDs and quantities.
        return mvc.perform(post("/api/shipping/quote").with(jwt().jwt(j->j.subject("local-test-user")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"zipCode\":\""+ADDRESS.get("zip")+"\",\"items\":[{"
                        +"\"productId\":"+product.getId()+",\"variantId\":"+variantId+",\"quantity\":2,"
                        +"\"supplierProductId\":\"FORGED\",\"supplierVariantId\":\"FORGED\",\"price\":0}]}"));
    }
    @Test void completeSpringHttpPathKeepsEvenZeroCountryQuoteBlocked() throws Exception {
        responds(success("0","CN").replace("\"free_shipping\":false","\"free_shipping\":true"));
        quote(variant.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.code").value("ALIEXPRESS_DESTINATION_NOT_CONFIRMED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));
        assertThat(httpCalls.get()).isEqualTo(1);verify(gateway).calculate(any());verify(tokens).existingAccessToken();
        var observed=evaluation.get();assertThat(observed.coverage()).isEqualTo(AliExpressFreightGateway.Coverage.COUNTRY_ONLY);
        assertThat(observed.lines().get(0).variantId()).isEqualTo(variant.getId());
        var method=observed.lines().get(0).methods().get(0);
        assertThat(method.serviceName()).isEqualTo("CAINIAO_STANDARD");assertThat(method.currency()).isEqualTo("BRL");
        assertThat(method.amount()).isEqualByComparingTo("0");assertThat(method.originCountry()).isEqualTo("CN");
        assertThat(method.estimatedDeliveryTime()).isEqualTo("5-12days");
    }
    @ParameterizedTest @CsvSource({"27,ALIEXPRESS_AUTHENTICATION_FAILED","11,ALIEXPRESS_PERMISSION_DENIED"})
    void providerTokenAndPermissionErrorsAreHandledAfterActualHttpParsing(String code,String safeCode) throws Exception {
        responds("{\"error_response\":{\"code\":"+code+",\"msg\":\"private-secret-not-for-browser\"}}");
        quote(variant.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value(safeCode))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret-not-for-browser"))));
        assertThat(httpCalls.get()).isEqualTo(1);assertThat(evaluation.get()).isNull();
    }
    @Test void providerRejectingSkuDoesNotOfferFallbackRates() throws Exception {
        responds("{\"aliexpress_ds_freight_query_response\":{\"result\":{\"success\":false,\"code\":\"DELIVERY_INFO_EMPTY\"}}}");
        quote(variant.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value("ALIEXPRESS_INVALID_PARAMETERS"));
        assertThat(httpCalls.get()).isEqualTo(1);assertThat(evaluation.get()).isNull();
    }
    @Test void skuFromAnotherLocalProductIsRejectedWithoutOAuthOrHttp() throws Exception {
        var other=products.saveAndFlush(new Product("Outro","ae-http-other",null,null,BigDecimal.TEN,null,"Acessorios",1,null,true));
        var foreign=new ProductVariant(other,"ALIEXPRESS","12000053109276548",PID,"FOREIGN","Outra",Map.of(),BigDecimal.ONE,"BRL",null,null,null,null,null);
        variants.saveAndFlush(foreign);
        quote(foreign.getId()).andExpect(status().isConflict());
        verifyNoInteractions(tokens);assertThat(httpCalls.get()).isZero();
    }
    @Test void unknownOriginIsNeverAssumedAndCannotAuthorizeCheckout() throws Exception {
        responds(success("0","ZZ").replace("\"free_shipping\":false","\"free_shipping\":true"));
        quote(variant.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value("ALIEXPRESS_DESTINATION_NOT_CONFIRMED"));
        assertThat(evaluation.get().lines().get(0).methods().get(0).originCountry()).isNull();
        assertThat(httpCalls.get()).isEqualTo(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"1.99","24.00","1230"})
    void ambiguousMoneyIsRejectedThroughTheCompleteCheckoutPath(String amount) throws Exception {
        responds(success(amount,"CN"));
        quote(variant.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value("ALIEXPRESS_AMBIGUOUS_MONEY"));
        assertThat(evaluation.get()).isNull();assertThat(httpCalls.get()).isEqualTo(1);
    }
}

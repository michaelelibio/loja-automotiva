package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AliExpressAssistedFreightPreviewTests {
    MockRestServiceServer server;AliExpressDsFreightClient client;
    AliExpressTokenService tokens=mock(AliExpressTokenService.class);
    AliExpressProductClient products=mock(AliExpressProductClient.class);
    AliExpressProductNormalizer normalizer=new AliExpressProductNormalizer();
    static final JsonMapper JSON=JsonMapper.builder().build();
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressDsFreightClient(builder.build(),"synthetic-key","synthetic-secret",Clock.systemUTC());
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
    }
    String response(String amount,String formatted,String free,String extra) {
        return "{\"result\":{\"success\":true,\"code\":\"200\",\"delivery_options\":[{\"code\":\"CAINIAO_STANDARD\","
            +"\"company\":\"AliExpress Standard Shipping\",\"ship_from_country\":\"CN\",\"min_delivery_days\":10,\"max_delivery_days\":25,"
            +"\"shipping_fee_currency\":\"BRL\",\"shipping_fee_cent\":\""+amount+"\",\"shipping_fee_format\":\""+formatted+"\",\"free_shipping\":"+free+extra+"}]}}";
    }
    void expect(String sku,int quantity,String body) {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(request->{
            var values=new HashMap<String,String>();
            for(var part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                var pair=part.split("=",2);values.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            assertThat(values.get("method")).isEqualTo("aliexpress.ds.freight.query");
            var input=JSON.readTree(values.get("queryDeliveryReq"));
            assertThat(input.path("selectedSkuId").asText()).isEqualTo(sku);
            assertThat(input.path("quantity").asInt()).isEqualTo(quantity);
            assertThat(input.path("province").asText()).isEqualTo("Santa Catarina");
            assertThat(input.size()).isEqualTo(8); // No CEP/city/recipient or undocumented fields.
        }).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    @Test void documentedRealDecimalObservationKeepsMetadataWithoutGuessingMoney() {
        expect("456",2,response("24.00","R$ 24,00","false",",\"ddpIncludeVATTax\":true"));
        var observed=client.queryObserved("synthetic-token",new AliExpressDsFreightClient.Input("123","456",2,"BR","Santa Catarina"));
        assertThat(observed.result().status()).isEqualTo(AliExpressFreightDiagnosticResult.Status.MONEY_SCALE_UNCONFIRMED);
        assertThat(observed.options()).singleElement().satisfies(option->{
            assertThat(option.code()).isEqualTo("CAINIAO_STANDARD");assertThat(option.name()).isEqualTo("AliExpress Standard Shipping");
            assertThat(option.originCountry()).isEqualTo("CN");assertThat(option.estimatedDays()).isEqualTo(25);
            assertThat(option.receivedAmount()).isEqualTo("24.00");assertThat(option.formattedAmount()).isEqualTo("24.00");
            assertThat(option.monetaryConsistency()).isEqualTo("MAJOR_MATCH");assertThat(option.shippingBrl()).isNull();
            assertThat(option.ddpIncludesVatTax()).isTrue();
        });server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"0","0.00"})
    void onlyUnconditionalZeroShippingIsScaleInvariant(String value) {
        expect("456",1,response(value,"R$ 0,00","true",""));
        var observed=client.queryObserved("synthetic-token",new AliExpressDsFreightClient.Input("123","456",1,"BR","Santa Catarina"));
        assertThat(observed.options().get(0).shippingBrl()).isEqualByComparingTo("0.00");server.verify();
    }
    @Test void platformErrorCannotSmuggleAConflictingSuccessWithFreeShipping() {
        expect("456",1,"{\"error_response\":{\"code\":\"11\"},"+response("0","0.00","true","").substring(1));
        var observed=client.queryObserved("synthetic-token",new AliExpressDsFreightClient.Input("123","456",1,"BR","Santa Catarina"));
        assertThat(observed.result().status()).isEqualTo(AliExpressFreightDiagnosticResult.Status.PERMISSION_DENIED);
        assertThat(observed.options()).isEmpty();assertThat(observed.deliveryOptions()).isEmpty();server.verify();
    }
    @Test void thresholdsContradictionsAndInvalidMetadataAreNotSilentlyNormalized() {
        expect("456",1,response("0","R$ 1,00","true",",\"free_shipping_threshold\":100").replace("\"CN\"","\"??\"").replace("\"max_delivery_days\":25","\"max_delivery_days\":2"));
        var option=client.queryObserved("synthetic-token",new AliExpressDsFreightClient.Input("123","456",1,"BR","Santa Catarina")).options().get(0);
        assertThat(option.shippingBrl()).isNull();assertThat(option.originCountry()).isNull();assertThat(option.estimatedDays()).isNull();
        assertThat(option.monetaryConsistency()).isEqualTo("CONFLICTING_FIELDS");server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"not-a-number","US $1.00","R$ 1,00"})
    void contradictoryOrUnrecognizedDisplayDoesNotBecomeFreeShipping(String formatted) {
        expect("456",1,response("0",formatted,"true",""));
        var option=client.queryObserved("synthetic-token",new AliExpressDsFreightClient.Input("123","456",1,"BR","Santa Catarina")).options().get(0);
        assertThat(option.shippingBrl()).isNull();server.verify();
    }
    @Test void productLookupAndEverySkuQuantityAreQueriedWithoutApprovingOrInventingTaxes() throws Exception {
        var raw=JSON.readTree(getClass().getResourceAsStream("/aliexpress-product-1005010661577657.json")).path("aliexpress_ds_product_get_response").path("result");
        var skuEntries=(tools.jackson.databind.node.ArrayNode)raw.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o");
        var second=(tools.jackson.databind.node.ObjectNode)JSON.readTree(skuEntries.get(0).toString());
        second.put("sku_id","999");second.put("price_include_tax",true);second.put("tax_amount","3.20");second.put("tax_currency_code","BRL");skuEntries.add(second);
        var preview=normalizer.normalize(raw,"1005010661577657","BR");
        var variant=preview.variants().get(0);assertThat(variant.currency()).isEqualTo("BRL");
        when(products.get("1005010661577657","BR","BRL","en","synthetic-token")).thenReturn(raw);
        expect(variant.skuId(),1,response("24.00","24.00","false",""));
        expect("999",2,response("48.00","48.00","false",""));
        var helper=new AliExpressAssistedFreightPreview(tokens,client,products,normalizer);
        var result=helper.preview(new AliExpressAssistedFreightPreview.Context(7L,new BigDecimal("100"),"SC",List.of(
            new AliExpressAssistedFreightPreview.Item("1005010661577657",variant.skuId(),1),
            new AliExpressAssistedFreightPreview.Item("1005010661577657","999",2))));
        assertThat(result.commercialAuthorized()).isFalse();assertThat(result.lines()).hasSize(2);
        assertThat(result.costs().observedProductsBrl()).isEqualByComparingTo(variant.price().multiply(new BigDecimal("3")));
        assertThat(result.costs().supplierTotalBrl()).isNull();assertThat(result.costs().taxesBrl()).isNull();
        assertThat(result.costs().netMarginBrl()).isNull();
        assertThat(result.lines().get(1).tax().priceIncludesTax()).isTrue();assertThat(result.lines().get(1).tax().receivedTaxAmount()).isEqualTo("3.20");
        assertThat(result.blockers()).contains("FULL_ADDRESS_AND_POSTAL_PRICE_NOT_CONFIRMED","TAXES_NOT_CONFIRMED","FREIGHT_SCALE_OR_CURRENCY_NOT_CONFIRMED");
        verify(products,times(1)).get(anyString(),anyString(),anyString(),anyString(),anyString());
        verify(tokens).existingAccessToken();verifyNoMoreInteractions(tokens);server.verify();
    }
    @Test void missingAuthorizationDoesNotQueryProductsOrFreight() {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("secret must not leak"));
        var result=new AliExpressAssistedFreightPreview(tokens,client,products,normalizer).preview(
            new AliExpressAssistedFreightPreview.Context(7L,new BigDecimal("50"),"SC",List.of(new AliExpressAssistedFreightPreview.Item("123","456",1))));
        assertThat(result.lines().get(0).status()).isEqualTo("LOCAL_AUTHORIZATION_UNAVAILABLE");
        assertThat(result.costs().observedProductsBrl()).isNull();assertThat(JSON.writeValueAsString(result)).doesNotContain("secret must not leak");
        verifyNoInteractions(products);server.verify();
    }
    @Test void rateLimitStopsOtherSkusWithoutRetriesOrInventingProductCosts() {
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("unavailable"));
        server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        var result=new AliExpressAssistedFreightPreview(tokens,client,products,normalizer).preview(
            new AliExpressAssistedFreightPreview.Context(7L,new BigDecimal("50"),"SC",List.of(
                new AliExpressAssistedFreightPreview.Item("123","456",1),new AliExpressAssistedFreightPreview.Item("999","789",2))));
        assertThat(result.lines()).allSatisfy(line->assertThat(line.status()).isEqualTo("RATE_LIMITED"));
        assertThat(result.costs().observedProductsBrl()).isNull();assertThat(result.commercialAuthorized()).isFalse();
        verify(products,times(1)).get(anyString(),anyString(),anyString(),anyString(),anyString());server.verify();
    }
    @Test void unknownCostsNeverBecomeZeroAndFutureEligibilityRequiresEveryProof() {
        var incomplete=AliExpressAssistedFreightPreview.Costs.calculate(new BigDecimal("50"),new BigDecimal("24"),null,BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("100"));
        assertThat(incomplete.supplierTotalBrl()).isNull();assertThat(incomplete.netMarginBrl()).isNull();
        var costs=AliExpressAssistedFreightPreview.Costs.calculate(new BigDecimal("50"),new BigDecimal("24"),new BigDecimal("10"),BigDecimal.ZERO,new BigDecimal("5"),new BigDecimal("100"));
        assertThat(costs.supplierTotalBrl()).isEqualByComparingTo("84");assertThat(costs.grossMarginBrl()).isEqualByComparingTo("16");assertThat(costs.netMarginBrl()).isEqualByComparingTo("11");
        var now=Instant.now();
        assertThat(new AliExpressAssistedFreightPreview.Evidence(costs,true,true,true,true,now.plusSeconds(60)).automaticEligible(now)).isTrue();
        assertThat(new AliExpressAssistedFreightPreview.Evidence(costs,false,true,true,true,now.plusSeconds(60)).automaticEligible(now)).isFalse();
        assertThat(new AliExpressAssistedFreightPreview.Evidence(costs,true,false,true,true,now.plusSeconds(60)).automaticEligible(now)).isFalse();
        assertThat(new AliExpressAssistedFreightPreview.Evidence(costs,true,true,true,true,now).automaticEligible(now)).isFalse();
        assertThat(new AliExpressAssistedFreightPreview.Evidence(incomplete,true,true,true,true,now.plusSeconds(60)).automaticEligible(now)).isFalse();
    }
}

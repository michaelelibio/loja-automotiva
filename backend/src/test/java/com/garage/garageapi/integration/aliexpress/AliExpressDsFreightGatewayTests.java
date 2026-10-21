package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AliExpressDsFreightGatewayTests {
    ProductRepository products=mock(ProductRepository.class);
    ProductVariantRepository variants=mock(ProductVariantRepository.class);
    AliExpressTokenService tokens=mock(AliExpressTokenService.class);
    Product product=mock(Product.class);
    ProductVariant variant=mock(ProductVariant.class);
    AliExpressDsFreightClient client=mock(AliExpressDsFreightClient.class);
    AliExpressDsFreightGateway gateway=new AliExpressDsFreightGateway(products,variants,tokens,client);
    static final String PID="1005010661577657",SKU="12000053109276547";
    @BeforeEach void setup() {
        when(products.findById(1L)).thenReturn(Optional.of(product));when(variants.findById(2L)).thenReturn(Optional.of(variant));
        when(product.getId()).thenReturn(1L);when(product.getSupplier()).thenReturn("ALIEXPRESS");
        when(product.getFulfillmentType()).thenReturn(FulfillmentType.DROPSHIPPING);when(product.getActive()).thenReturn(true);
        when(product.canFulfill(anyInt())).thenReturn(true);when(product.getSupplierProductId()).thenReturn(PID);
        when(variant.getProduct()).thenReturn(product);when(variant.getActive()).thenReturn(true);
        when(variant.getSupplier()).thenReturn("ALIEXPRESS");when(variant.getSupplierProductId()).thenReturn(PID);
        when(variant.getSupplierVariantId()).thenReturn(SKU);when(variant.getSupplierStockQuantity()).thenReturn(10);
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
    }
    static ShippingProvider.Request request() {
        // Deliberately forged hints: the adapter must re-read all supplier identifiers.
        return new ShippingProvider.Request("89229030",List.of(new ShippingProvider.Item(1L,2L,1,
                new BigDecimal("-100"),FulfillmentType.LOCAL_STOCK,"CJ","FORGED")));
    }
    static String response(String cents) {
        return "{\"aliexpress_ds_freight_query_response\":{\"result\":{\"code\":200,\"success\":true,\"delivery_options\":[{"
                +"\"code\":\"STANDARD\",\"shipping_fee_cent\":\""+cents+"\",\"shipping_fee_currency\":\"BRL\",\"free_shipping\":false,"
                +"\"company\":\"Standard\",\"ship_from_country\":\"CN\",\"min_delivery_days\":5,\"max_delivery_days\":8}]}}}";
    }
    @Test void realSharedHttpClientReceivesOnlyTrustedIdsAndDocumentedCountryFields() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        var shared=new AliExpressDsFreightClient(builder.build(),"synthetic-key","synthetic-secret",Clock.systemUTC());
        var adapter=new AliExpressDsFreightGateway(products,variants,tokens,shared);
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(req->{
            Map<String,String> form=new HashMap<>();
            for(String part:((MockClientHttpRequest)req).getBodyAsString().split("&")) {
                var pair=part.split("=",2);form.put(pair[0],java.net.URLDecoder.decode(pair[1],java.nio.charset.StandardCharsets.UTF_8));
            }
            assertThat(form.get("method")).isEqualTo("aliexpress.ds.freight.query");
            var dto=tools.jackson.databind.json.JsonMapper.builder().build().readTree(form.get("queryDeliveryReq"));
            assertThat(dto.path("productId").asText()).isEqualTo(PID);assertThat(dto.path("selectedSkuId").asText()).isEqualTo(SKU);
            assertThat(dto.path("shipToCountry").asText()).isEqualTo("BR");assertThat(dto.size()).isEqualTo(7);
            assertThat(dto.toString()).doesNotContain("89229030","FORGED","province","city","price");
        }).andRespond(withSuccess(response("1230"),MediaType.APPLICATION_JSON));
        var result=adapter.calculate(request());
        assertThat(result.coverage()).isEqualTo(AliExpressFreightGateway.Coverage.COUNTRY_ONLY);
        assertThat(result.lines().get(0).methods().get(0).amount()).isEqualByComparingTo("12.30");
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();server.verify();
    }
    @ParameterizedTest @CsvSource({"PERMISSION_DENIED,PROVIDER_PERMISSION_DENIED,PERMISSION_DENIED",
            "TOKEN_REJECTED,PROVIDER_TOKEN_REJECTED,AUTHENTICATION_FAILED",
            "PRODUCT_UNAVAILABLE,NO_DELIVERY_METHODS,FREIGHT_UNAVAILABLE",
            "PARAMETER_ERROR,PROVIDER_PARAMETER_REJECTED,INVALID_PARAMETERS",
            "INVALID_RESPONSE,AMBIGUOUS_MONETARY_UNIT,AMBIGUOUS_MONEY",
            "INVALID_RESPONSE,INVALID_DELIVERY_METHOD,INVALID_DELIVERY_METHOD",
            "COMMUNICATION_ERROR,COMMUNICATION_FAILURE,EXTERNAL_FAILURE"})
    void normalizedFailuresRemainDistinctAndDoNotLeakMessages(AliExpressFreightDiagnosticResult.Status status,String safe,
            AliExpressShippingException.Code code) {
        when(client.query(anyString(),any())).thenReturn(AliExpressFreightDiagnosticResult.failure(status,safe));
        assertThatThrownBy(()->gateway.calculate(request())).isInstanceOfSatisfying(AliExpressShippingException.class,
                ex->{assertThat(ex.code()).isEqualTo(code);assertThat(ex.toString()).doesNotContain("synthetic-token",PID,SKU,"89229030");});
        verify(client).query("synthetic-token",new AliExpressDsFreightClient.Input(PID,SKU,1,"BR"));
        verify(tokens,never()).accessToken();
    }
    @Test void foreignVariantIsRejectedBeforeReadingOAuth() {
        var foreign=mock(Product.class);when(foreign.getId()).thenReturn(77L);when(variant.getProduct()).thenReturn(foreign);
        assertThatThrownBy(()->gateway.calculate(request())).isInstanceOfSatisfying(AliExpressShippingException.class,
                ex->assertThat(ex.code()).isEqualTo(AliExpressShippingException.Code.INVALID_PARAMETERS));
        verifyNoInteractions(tokens,client);
    }
    @Test void divergentSupplierProductIdIsRejectedBeforeReadingOAuth() {
        when(variant.getSupplierProductId()).thenReturn("999999999999");
        assertThatThrownBy(()->gateway.calculate(request())).isInstanceOf(AliExpressShippingException.class);
        verifyNoInteractions(tokens,client);
    }
    @Test void unreadablePersistedAuthorizationNeverRefreshesOrCallsSupplier() {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("private-token"));
        assertThatThrownBy(()->gateway.calculate(request())).isInstanceOfSatisfying(AliExpressShippingException.class,
                ex->assertThat(ex.code()).isEqualTo(AliExpressShippingException.Code.AUTHENTICATION_FAILED));
        verify(tokens,never()).accessToken();verifyNoInteractions(client);
    }
    @Test void duplicateLocalLinesAreAggregatedBeforeOneSkuRequest() {
        when(client.query(anyString(),any())).thenReturn(AliExpressFreightDiagnosticResult.result(AliExpressFreightDiagnosticResult.Status.SUCCESS,
                "CALL_SUCCEEDED",List.of(new AliExpressFreightDiagnosticResult.Method("STANDARD",BigDecimal.ONE,"BRL",null))));
        var line=request().items().get(0);gateway.calculate(new ShippingProvider.Request("89229030",List.of(line,line)));
        verify(client).query("synthetic-token",new AliExpressDsFreightClient.Input(PID,SKU,2,"BR"));verifyNoMoreInteractions(client);
    }
    @Test void realParserRejectsAmbiguousMoneyBeforeAnyRateCanBeSelected() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(response("1.99"),MediaType.APPLICATION_JSON));
        var adapter=new AliExpressDsFreightGateway(products,variants,tokens,
                new AliExpressDsFreightClient(builder.build(),"synthetic-key","synthetic-secret",Clock.systemUTC()));
        assertThatThrownBy(()->adapter.calculate(request())).isInstanceOfSatisfying(AliExpressShippingException.class,
                ex->assertThat(ex.code()).isEqualTo(AliExpressShippingException.Code.AMBIGUOUS_MONEY));server.verify();
    }
}

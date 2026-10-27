package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AliExpressFreightProvinceClientTests {
    MockRestServiceServer server;
    AliExpressFreightDiagnosticClient client;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressFreightDiagnosticClient(builder.build(),"synthetic-key","synthetic-secret",Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
    }
    String body() {
        return AliExpressFreightDiagnosticClientTests.response("["+AliExpressFreightDiagnosticClientTests.method("STANDARD","\"24.00\"","BRL")
                .replace("\"code\":","\"shipping_fee_format\":\"R$ 24,00\",\"code\":")+"]");
    }
    void expect(String province) {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(request->{
            Map<String,String> form=new TreeMap<>();
            for(String part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                var pair=part.split("=",2);form.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            assertThat(form).containsEntry("method","aliexpress.ds.freight.query").containsEntry("session","synthetic-token");
            var dto=JsonMapper.builder().build().readTree(form.get("queryDeliveryReq"));
            assertThat(dto.path("productId").asText()).isEqualTo("123");assertThat(dto.path("selectedSkuId").asText()).isEqualTo("456");
            assertThat(dto.path("quantity").asInt()).isEqualTo(2);assertThat(dto.path("shipToCountry").asText()).isEqualTo("BR");
            assertThat(dto.has("provinceCode") || dto.has("cityCode") || dto.has("zip")).isFalse();
            if(province==null) { assertThat(dto.has("province")).isFalse();assertThat(dto.size()).isEqualTo(7); }
            else { assertThat(dto.path("province").asText()).isEqualTo(province);assertThat(dto.size()).isEqualTo(8); }
            // The signature must cover the exact DTO, including the literal province.
            String supplied=form.remove("sign");StringBuilder canonical=new StringBuilder();form.forEach((k,v)->canonical.append(k).append(v));
            try {
                var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                assertThat(supplied).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            } catch(Exception ex) { throw new AssertionError(ex); }
        }).andRespond(withSuccess(body(),MediaType.APPLICATION_JSON));
    }
    @Test void onlyProvinceDiffersAndRealWireObservationsRetainMonetaryBlock() {
        expect(null);expect("Santa Catarina");
        var baseline=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",2,"BR"));
        var province=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",2,"BR","Santa Catarina"));
        assertThat(baseline.request().queryDeliveryReqFields()).doesNotContain("province","provinceCode","cityCode");
        assertThat(province.request().queryDeliveryReqFields()).contains("province").doesNotContain("provinceCode","cityCode");
        assertThat(province.request().province()).isEqualTo("Santa Catarina");
        assertThat(baseline.result().limitations()).contains("COUNTRY_ONLY_BR_DIAGNOSTIC");
        assertThat(province.result().limitations()).contains("COUNTRY_AND_PROVINCE_DIAGNOSTIC_NOT_ADDRESS_CONFIRMED").doesNotContain("COUNTRY_ONLY_BR_DIAGNOSTIC");
        for(var observation:List.of(baseline,province)) {
            assertThat(observation.request().requestBodyWritten()).isTrue();assertThat(observation.request().httpResponseReceived()).isTrue();
            assertThat(observation.result().safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");assertThat(observation.result().methods()).isEmpty();
            assertThat(observation.result().monetaryStructure()).anySatisfy(f->{
                assertThat(f.path()).isEqualTo("shipping_fee_format");assertThat(f.safeValue()).isEqualTo("24.00");assertThat(f.comparison()).isEqualTo("MAJOR_MATCH");
            });
            assertThat(observation.toString()).doesNotContain("synthetic-token","synthetic-secret");
        }
        assertThat(AliExpressFreightProvinceComparison.of("COMPLETED","COMPARISON_COLLECTED",baseline,province).responseComparison()).isEqualTo("NO_OBSERVED_DIFFERENCE");
        server.verify();
    }
    @Test void arbitraryProvinceCannotTriggerNetworkOrClaimSentParameters() {
        var observation=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR","invented-code"));
        assertThat(observation.result().safeCode()).isEqualTo("INVALID_TEST_INPUT");
        assertThat(observation.request().requestBodyWritten()).isFalse();assertThat(observation.request().queryDeliveryReqFields()).isEmpty();server.verify();
    }
    @Test void transportFailureCannotBeReportedAsReceivedOrAnUnchangedQuote() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-token")));
        var observation=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR","Santa Catarina"));
        assertThat(observation.request().httpResponseReceived()).isFalse();assertThat(observation.result().safeCode()).isEqualTo("COMMUNICATION_FAILURE");
        assertThat(AliExpressFreightProvinceComparison.of("COMPLETED","COMPARISON_COLLECTED",observation,observation).responseComparison()).isEqualTo("INSUFFICIENT_OBSERVABLE_DATA");server.verify();
    }
    @ParameterizedTest @CsvSource({"BR,BR,SAME_COUNTRY_ROUTE_CANDIDATE","CN,CN,CROSS_BORDER_ROUTE_OBSERVED",
            "ZZ,,ORIGIN_NOT_CONFIRMED","synthetic-token,,ORIGIN_NOT_CONFIRMED"})
    void monetaryRejectionCannotHideOriginOrTurnItIntoApprovedShipping(String supplied,String safeOrigin,String route) {
        server.expect(anything()).andRespond(withSuccess(body().replace("\"ship_from_country\":\"BR\"","\"ship_from_country\":\""+supplied+"\""),MediaType.APPLICATION_JSON));
        var observation=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR"));
        assertThat(observation.result().safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");assertThat(observation.result().methods()).isEmpty();
        assertThat(observation.deliveryOptions()).containsExactly(new AliExpressDsFreightClient.DeliveryEvidence(0,"STANDARD",safeOrigin,route));
        assertThat(observation.toString()).doesNotContain("synthetic-token");server.verify();
    }
    @Test void missingOriginNeverDefaultsToCNAndOnlySanitizedDeliveryCodeEscapes() {
        server.expect(anything()).andRespond(withSuccess(body().replace("\"ship_from_country\":\"BR\",","").replace("\"STANDARD\"","\"synthetic-token\""),MediaType.APPLICATION_JSON));
        var observation=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR"));
        assertThat(observation.deliveryOptions()).containsExactly(new AliExpressDsFreightClient.DeliveryEvidence(0,null,null,"ORIGIN_NOT_CONFIRMED"));server.verify();
    }
    @Test void differentOriginIsAnObservedDifferenceEvenWithEqualMoney() {
        var result=AliExpressFreightDiagnosticResult.failure(AliExpressFreightDiagnosticResult.Status.INVALID_RESPONSE,"AMBIGUOUS_MONETARY_UNIT")
                .withMonetaryStructure(List.of(new AliExpressFreightDiagnosticResult.MoneyField(0,"shipping_fee_cent",true,"STRING","BRL","CENT_1_100_DESCRIPTION_EXAMPLE_CONFLICT","24.00")));
        var request=new AliExpressDsFreightClient.RequestEvidence(List.of("shipToCountry"),"BR",null,true,true);
        var a=new AliExpressDsFreightClient.Observation(request,result,List.of(new AliExpressDsFreightClient.DeliveryEvidence(0,"STANDARD","CN","CROSS_BORDER_ROUTE_OBSERVED")));
        var b=new AliExpressDsFreightClient.Observation(request,result,List.of(new AliExpressDsFreightClient.DeliveryEvidence(0,"STANDARD","BR","SAME_COUNTRY_ROUTE_CANDIDATE")));
        assertThat(AliExpressFreightProvinceComparison.of("COMPLETED","COMPARISON_COLLECTED",a,b).responseComparison()).isEqualTo("OBSERVED_DIFFERENCE");
    }
    @Test void serviceCodeWithNumericPrefixIsPreservedAsAnOpaqueIdentifier() {
        server.expect(anything()).andRespond(withSuccess(body().replace("\"STANDARD\"","\"4PX_STANDARD\""),MediaType.APPLICATION_JSON));
        var observation=client.queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR"));
        assertThat(observation.deliveryOptions().get(0).serviceCode()).isEqualTo("4PX_STANDARD");server.verify();
    }
}

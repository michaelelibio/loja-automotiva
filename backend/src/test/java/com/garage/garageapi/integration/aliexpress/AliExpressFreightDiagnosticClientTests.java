package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressFreightDiagnosticClientTests {
    MockRestServiceServer server;
    AliExpressFreightDiagnosticClient client;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressFreightDiagnosticClient(builder.build(),"synthetic-key","synthetic-secret",
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"),ZoneOffset.UTC));
    }
    static AliExpressFreightDiagnosticClient.Input input() { return new AliExpressFreightDiagnosticClient.Input("1005010661577657","12000053109276547",1,"BR"); }
    static String response(String entries) {
        return "{\"aliexpress_ds_freight_query_response\":{\"result\":{\"success\":true,\"code\":200,\"delivery_options\":"+entries+"}}}";
    }
    static String method(String code,String cents,String currency) {
        return "{\"code\":\""+code+"\",\"min_delivery_days\":5,\"max_delivery_days\":8,\"company\":\"AliExpress Standard\",\"ship_from_country\":\"BR\","
            +"\"free_shipping\":false,\"shipping_fee_cent\":"+cents+",\"shipping_fee_currency\":\""+currency+"\"}";
    }
    @Test void officialCurrentTopTransportAndRealSkuAreUsedOnce(CapturedOutput output) {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(request->{
                    Map<String,String> params=new TreeMap<>();
                    for(String part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                        var pair=part.split("=",2);params.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
                    }
                    String sign=params.remove("sign");
                    assertThat(params).containsEntry("sign_method","sha256").containsEntry("timestamp","1792065600000")
                            .containsEntry("method","aliexpress.ds.freight.query").containsEntry("session","synthetic-token");
                    assertThat(params).doesNotContainKeys("app_secret","access_token","sku_id");
                    var dto=tools.jackson.databind.json.JsonMapper.builder().build().readTree(params.get("queryDeliveryReq"));
                    assertThat(dto.path("productId").asText()).isEqualTo("1005010661577657");
                    assertThat(dto.path("selectedSkuId").asText()).isEqualTo("12000053109276547");
                    assertThat(dto.path("shipToCountry").asText()).isEqualTo("BR");
                    assertThat(dto.has("price")).isFalse();
                    assertThat(dto.size()).isEqualTo(7);
                    assertThat(dto.path("productId").isString()).isTrue();
                    assertThat(dto.path("selectedSkuId").isString()).isTrue();
                    assertThat(dto.path("currency").asText()).isEqualTo("BRL");
                    assertThat(dto.path("language").asText()).isEqualTo("pt_BR");
                    assertThat(dto.path("locale").asText()).isEqualTo("pt_BR");
                    try {
                        var input=new StringBuilder();params.forEach((key,value)->input.append(key).append(value));
                        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                        assertThat(sign).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
                    } catch(Exception exception) { throw new AssertionError(exception); }
                }).andRespond(withSuccess(response("["+method("CAINIAO_STANDARD","\"1230\"","BRL")+","+method("EXPRESS","\"2539\"","USD")+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token", input());
        assertThat(result.status()).isEqualTo(MONEY_SCALE_UNCONFIRMED);assertThat(result.methods()).isEmpty();
        assertThat(result.monetaryStructure()).anySatisfy(field->{
            assertThat(field.path()).isEqualTo("shipping_fee_cent");assertThat(field.safeValue()).isEqualTo("1230");
            assertThat(field.syntacticallyValidNumber()).isTrue();
        });
        assertThat(result.limitations()).contains("APP_API_PERMISSION_NOT_PRECONFIRMED","DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED");
        assertThat(output.getAll()).doesNotContain("synthetic-token","synthetic-secret","aeop_freight_calculate_result_for_buyer");
        server.verify();
    }
    @ParameterizedTest
    @CsvSource({"11,isv.permission-api-package-limit,PERMISSION_DENIED","27,invalid-sessionkey,TOKEN_REJECTED",
            "50,isv.invalid-parameter,PARAMETER_ERROR","25,unknown,PROTOCOL_ERROR","22,unknown,PROTOCOL_ERROR",
            "7,unknown,RATE_LIMITED","50,unknown,PROVIDER_REJECTED"})
    void classifiesOnlyKnownCodesNeverProviderMessages(String code,String sub,AliExpressFreightDiagnosticResult.Status expected,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":"+code+",\"sub_code\":\""+sub+"\",\"msg\":\"synthetic-secret synthetic-token CPF 52998224725\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token", input());
        assertThat(result.status()).isEqualTo(expected);assertThat(result.methods()).isEmpty();
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-secret","synthetic-token","52998224725");server.verify();
    }
    @Test void supportsDocumentedSimplifiedResponseAndStrictStringBooleans() {
        String body=response("["+method("STANDARD","\"1230\"","BRL")+"]")
                .replace("\"success\":true","\"success\":\"true\"")
                .replace("\"free_shipping\":false","\"free_shipping\":\"false\"");
        var parsed=tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).path("aliexpress_ds_freight_query_response");
        server.expect(anything()).andRespond(withSuccess(parsed.toString(),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).status()).isEqualTo(MONEY_SCALE_UNCONFIRMED);server.verify();
    }
    @Test void nonBrazilianDestinationCannotTriggerNetwork() {
        assertThat(client.query("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,null)).safeCode()).isEqualTo("INVALID_TEST_INPUT");server.verify();
    }
    @Test void unavailableProductDoesNotMeanFreeShipping() {
        server.expect(anything()).andRespond(withSuccess(response("[]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token", input()).status()).isEqualTo(PRODUCT_UNAVAILABLE);server.verify();
    }
    @ParameterizedTest
    @CsvSource({"21,TOP_21_MISSING_METHOD","22,TOP_22_INVALID_METHOD","24,TOP_24_MISSING_SIGNATURE",
            "25,TOP_25_INVALID_SIGNATURE","28,TOP_28_MISSING_APP_KEY","29,TOP_29_INVALID_APP_KEY"})
    void preservesExactPlatformProtocolCodeWithoutRawProviderDetails(String code,String expected,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":"+code
                +",\"msg\":\"synthetic-token synthetic-secret\",\"sub_msg\":\"private-signature\",\"request_id\":\"private-trace\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token", input());
        assertThat(result.status()).isEqualTo(PROTOCOL_ERROR);
        assertThat(result.safeCode()).isEqualTo(expected);
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-token","synthetic-secret","private-signature","private-trace");
        server.verify();
    }
    @Test void stringPlatformCodeHasSameMeaningAndCannotBeOverriddenByContradictorySubcode() {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":\"25\",\"sub_code\":\"isv.permission-denied\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token", input());
        assertThat(result.status()).isEqualTo(PROTOCOL_ERROR);
        assertThat(result.safeCode()).isEqualTo("TOP_25_INVALID_SIGNATURE");server.verify();
    }
    @ParameterizedTest @CsvSource({"7","11","22","25","26","27","29"})
    void businessErrorNumbersAreNotMisclassifiedAsTopPlatformCodes(String code) {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_ds_freight_query_response\":{\"result\":{\"success\":false,\"error_code\":"+code+"}}}",MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token", input()).status()).isEqualTo(PROVIDER_REJECTED);server.verify();
    }
    @Test void timeoutIsCommunicationFailureWithNoRetry(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-secret")));
        var result=client.query("synthetic-token", input());
        assertThat(result.status()).isEqualTo(COMMUNICATION_ERROR);assertThat(result.methods()).isEmpty();
        assertThat(output.getAll()).doesNotContain("synthetic-secret");server.verify();
    }
    @Test void ambiguousHttpDenialDoesNotProveApiPermission() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("synthetic-secret"));
        assertThat(client.query("synthetic-token", input()).status()).isEqualTo(COMMUNICATION_ERROR);server.verify();
    }
    @Test void malformedJsonIsNotACommunicationOrPermissionRejection() {
        server.expect(anything()).andRespond(withSuccess("<html>synthetic-token</html>",MediaType.TEXT_HTML));
        assertThat(client.query("synthetic-token", input()).status()).isEqualTo(INVALID_RESPONSE);server.verify();
    }
    @Test void decimalCentsFromOfficialExampleAreNeverGuessed() {
        server.expect(anything()).andRespond(withSuccess(response("["+method("STANDARD","\"1.99\"","USD")+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");server.verify();
    }
    @ParameterizedTest
    @CsvSource(value={"R$ 24,00|24.00|MAJOR_MATCH", "BRL 24.00|24.00|MAJOR_MATCH",
            "R$ 0,24|0.24|MINOR_MATCH", "R$ 25,00|25.00|NEITHER_MATCH",
            "R$ 24,00 CPF 52998224725||UNAVAILABLE", "US $24.00||UNAVAILABLE",
            "R$ 1.234,56||UNAVAILABLE", "R$ 24,00 synthetic-token||UNAVAILABLE"}, delimiter='|')
    void productionDecimalRemainsBlockedWhileDisplayIsComparedSafely(String formatted,String safe,String comparison,CapturedOutput output) {
        String entry=method("STANDARD","\"24.00\"","BRL").replace("\"code\":",
                "\"shipping_fee_format\":\""+formatted+"\",\"code\":");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token",input());
        assertThat(result.safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");
        assertThat(result.methods()).isEmpty();
        assertThat(result.monetaryStructure()).anySatisfy(f->{
            assertThat(f.path()).isEqualTo("shipping_fee_format");
            assertThat(f.optionIndex()).isZero(); assertThat(f.currency()).isEqualTo("BRL");
            assertThat(f.safeValue()).isEqualTo(safe); assertThat(f.comparison()).isEqualTo(comparison);
        });
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-token","52998224725");
        server.verify();
    }
    @Test void rejectedDocumentedExampleRetainsOnlySanitizedMonetaryShape(CapturedOutput output) {
        String entry=method("STANDARD","\"1.99\"","USD").replace("\"code\":",
                "\"shipping_fee_format\":\"US $1.99 synthetic-token CPF 52998224725\",\"recipient\":\"private-name\",\"code\":");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token",input());
        assertThat(result.safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");
        assertThat(result.methods()).isEmpty();
        assertThat(result.monetaryStructure()).anySatisfy(f->{
            assertThat(f.path()).isEqualTo("shipping_fee_cent"); assertThat(f.jsonType()).isEqualTo("STRING");
            assertThat(f.safeValue()).isEqualTo("1.99"); assertThat(f.currency()).isEqualTo("USD");
        }).anySatisfy(f->{ assertThat(f.path()).isEqualTo("shipping_fee"); assertThat(f.present()).isFalse(); });
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-token","52998224725","private-name","US $1.99");
        server.verify();
    }
    @ParameterizedTest @CsvSource({"shipping_fee","amount","cent","currency_code"})
    void undocumentedAlternateMoneyAlwaysBlocksEvenAlongsideValidCents(String field) {
        String entry=method("STANDARD","1230","BRL").replace("\"code\":","\""+field+"\":{\"amount\":\"99.99\",\"cent\":9999,\"currency_code\":\"USD\"},\"code\":");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token",input());
        assertThat(result.safeCode()).isEqualTo("CONFLICTING_MONETARY_FIELDS"); assertThat(result.methods()).isEmpty();
        assertThat(result.monetaryStructure()).anySatisfy(f->{ assertThat(f.path()).isEqualTo("shipping_fee_cent"); assertThat(f.jsonType()).isEqualTo("NUMBER"); });
        server.verify();
    }
    @Test void missingAmountNeverDefaultsToZero() {
        server.expect(anything()).andRespond(withSuccess(response("["+method("STANDARD","null","BRL")+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).status()).isEqualTo(INVALID_RESPONSE);server.verify();
    }
    @Test void conflictingFreeShippingIsNotAccepted() {
        String body=response("["+method("STANDARD","\"1230\"","BRL")+"]").replace("\"free_shipping\":false","\"free_shipping\":true");
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).safeCode()).isEqualTo("CONFLICTING_MONETARY_FIELDS");server.verify();
    }
    @ParameterizedTest @CsvSource({"DELIVERY_NOT_AVAILABLE_TO_YOUR_ADDRESS,PRODUCT_UNAVAILABLE,PROVIDER_DELIVERY_UNAVAILABLE","DELIVERY_INFO_EMPTY,PARAMETER_ERROR,PROVIDER_PRODUCT_ID_REJECTED"})
    void classifiesDocumentedBusinessErrors(String code,AliExpressFreightDiagnosticResult.Status status,String safe) {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_ds_freight_query_response\":{\"result\":{\"success\":false,\"code\":\""+code+"\"}}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token",input());assertThat(result.status()).isEqualTo(status);assertThat(result.safeCode()).isEqualTo(safe);server.verify();
    }
    @Test void exactCentsAndProviderOriginArePreserved() {
        server.expect(anything()).andRespond(withSuccess(response("["+method("STANDARD","\"9999999999999999\"","BRL")+"]"),MediaType.APPLICATION_JSON));
        var observation=client.queryObserved("synthetic-token",input());
        assertThat(observation.result().status()).isEqualTo(MONEY_SCALE_UNCONFIRMED);
        assertThat(observation.result().methods()).isEmpty();
        assertThat(observation.deliveryOptions()).singleElement().satisfies(option->assertThat(option.shipFromCountry()).isEqualTo("BR"));
        server.verify();
    }
    @Test void contradictoryFormattedAmountDoesNotCauseHundredfoldUnderstatement() {
        String entry=method("STANDARD","\"199\"","USD").replace("\"code\":", "\"shipping_fee_format\":\"US $199.00\",\"code\":");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).safeCode()).isEqualTo("AMBIGUOUS_MONETARY_UNIT");server.verify();
    }
    @Test void explicitFreeShippingRemainsZeroWithoutInventingPaidRates() {
        String entry=method("STANDARD","\"0\"","BRL").replace("\"free_shipping\":false","\"free_shipping\":\"true\"");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token",input());assertThat(result.status()).isEqualTo(SUCCESS);
        assertThat(result.methods().get(0).amount()).isEqualByComparingTo("0.00");
        assertThat(result.methods().get(0).amount().toPlainString()).isEqualTo("0.00");server.verify();
    }
    @Test void duplicateServiceCodesAreRejected() {
        String entry=method("STANDARD","\"1230\"","BRL");
        server.expect(anything()).andRespond(withSuccess(response("["+entry+","+entry+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).safeCode()).isEqualTo("DUPLICATE_SERVICE");server.verify();
    }
    @ParameterizedTest @CsvSource({"\"TRUE\"","1","null"})
    void successBooleanCannotBeCoerced(String success) {
        String body=response("[]").replace("\"success\":true","\"success\":"+success);
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token",input()).status()).isEqualTo(INVALID_RESPONSE);server.verify();
    }
    @Test void successfulFalseWithUnknownErrorRemainsInconclusive() {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_ds_freight_query_response\":{\"result\":{\"success\":false,\"error_desc\":\"permission synthetic-token\"}}}",MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token", input()).status()).isEqualTo(PROVIDER_REJECTED);server.verify();
    }
}

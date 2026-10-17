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
        var builder=RestClient.builder().baseUrl("https://eco.taobao.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressFreightDiagnosticClient(builder.build(),"synthetic-key","synthetic-secret",
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"),ZoneOffset.UTC));
    }
    static String response(String entries) {
        return "{\"aliexpress_logistics_buyer_freight_calculate_response\":{\"result\":{\"success\":true,"
                + "\"aeop_freight_calculate_result_for_buyer_d_t_o_list\":{\"aeop_freight_calculate_result_for_buyer_dto\":"+entries+"}}}}";
    }
    static String method(String name,String amount,String currency,String cent) {
        return "{\"error_code\":0,\"service_name\":\""+name+"\",\"estimated_delivery_time\":\"5-8day\","
                + "\"freight\":{\"amount\":"+amount+",\"currency_code\":\""+currency+"\""+(cent==null?"":",\"cent\":"+cent)+"}}";
    }
    @Test void officialTransportSignatureAndFixedDtoAreUsedOnceWithoutUnsupportedSku(CapturedOutput output) {
        server.expect(requestTo("https://eco.taobao.com/router/rest")).andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(request->{
                    Map<String,String> params=new TreeMap<>();
                    for(String part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                        var pair=part.split("=",2);params.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
                    }
                    String sign=params.remove("sign");
                    assertThat(params).containsEntry("sign_method","hmac").containsEntry("timestamp","2026-10-15 20:00:00")
                            .containsEntry("method","aliexpress.logistics.buyer.freight.calculate").containsEntry("session","synthetic-token");
                    assertThat(params).doesNotContainKeys("app_secret","access_token","sku_id");
                    assertThat(params.get("param_aeop_freight_calculate_for_buyer_d_t_o"))
                            .isEqualTo("{\"product_id\":1005010661577657,\"product_num\":1,\"country_code\":\"BR\",\"send_goods_country_code\":\"CN\",\"price\":\"25.39\",\"price_currency\":\"BRL\"}");
                    try {
                        var input=new StringBuilder();params.forEach((key,value)->input.append(key).append(value));
                        var mac=Mac.getInstance("HmacMD5");mac.init(new SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacMD5"));
                        assertThat(sign).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
                    } catch(Exception exception) { throw new AssertionError(exception); }
                }).andRespond(withSuccess(response("["+method("CAINIAO_STANDARD","12.30","BRL","1230")+","+method("EXPRESS","\"25.39\"","USD",null)+"]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.status()).isEqualTo(SUCCESS);assertThat(result.methods()).hasSize(2);
        assertThat(result.methods().get(0).amount()).isEqualByComparingTo("12.30");
        assertThat(result.methods().get(1).amount()).isEqualByComparingTo("25.39");
        assertThat(result.methods().get(1).currency()).isEqualTo("USD");
        assertThat(result.methods().get(0).estimatedDeliveryTime()).isEqualTo("5-8day");
        assertThat(result.limitations()).contains("CN_ORIGIN_UNCONFIRMED","SKU_ID_NOT_DOCUMENTED_NOT_SENT");
        assertThat(output.getAll()).doesNotContain("synthetic-token","synthetic-secret","aeop_freight_calculate_result_for_buyer");
        server.verify();
    }
    @ParameterizedTest
    @CsvSource({"11,isv.permission-api-package-limit,PERMISSION_DENIED","27,invalid-sessionkey,TOKEN_REJECTED",
            "50,isv.invalid-parameter,PARAMETER_ERROR","25,unknown,PROTOCOL_ERROR","22,unknown,PROTOCOL_ERROR",
            "7,unknown,RATE_LIMITED","50,unknown,PROVIDER_REJECTED"})
    void classifiesOnlyKnownCodesNeverProviderMessages(String code,String sub,AliExpressFreightDiagnosticResult.Status expected,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":"+code+",\"sub_code\":\""+sub+"\",\"msg\":\"synthetic-secret synthetic-token CPF 52998224725\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.status()).isEqualTo(expected);assertThat(result.methods()).isEmpty();
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-secret","synthetic-token","52998224725");server.verify();
    }
    @Test void unavailableProductDoesNotMeanFreeShipping() {
        server.expect(anything()).andRespond(withSuccess(response("[]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").status()).isEqualTo(PRODUCT_UNAVAILABLE);server.verify();
    }
    @ParameterizedTest
    @CsvSource({"21,TOP_21_MISSING_METHOD","22,TOP_22_INVALID_METHOD","24,TOP_24_MISSING_SIGNATURE",
            "25,TOP_25_INVALID_SIGNATURE","28,TOP_28_MISSING_APP_KEY","29,TOP_29_INVALID_APP_KEY"})
    void preservesExactPlatformProtocolCodeWithoutRawProviderDetails(String code,String expected,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":"+code
                +",\"msg\":\"synthetic-token synthetic-secret\",\"sub_msg\":\"private-signature\",\"request_id\":\"private-trace\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.status()).isEqualTo(PROTOCOL_ERROR);
        assertThat(result.safeCode()).isEqualTo(expected);
        assertThat(result.toString()+output.getAll()).doesNotContain("synthetic-token","synthetic-secret","private-signature","private-trace");
        server.verify();
    }
    @Test void stringPlatformCodeHasSameMeaningAndCannotBeOverriddenByContradictorySubcode() {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":\"25\",\"sub_code\":\"isv.permission-denied\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.status()).isEqualTo(PROTOCOL_ERROR);
        assertThat(result.safeCode()).isEqualTo("TOP_25_INVALID_SIGNATURE");server.verify();
    }
    @ParameterizedTest @CsvSource({"7","11","22","25","26","27","29"})
    void businessErrorNumbersAreNotMisclassifiedAsTopPlatformCodes(String code) {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_logistics_buyer_freight_calculate_response\":{\"result\":{\"success\":false,\"error_code\":"+code+"}}}",MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").status()).isEqualTo(PROVIDER_REJECTED);server.verify();
    }
    @Test void perMethodErrorCodeIsNotAnInvalidSignatureError() {
        server.expect(anything()).andRespond(withSuccess(response("[{\"error_code\":25}]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("UNCLASSIFIED_PROVIDER_REJECTION");server.verify();
    }
    @Test void timeoutIsCommunicationFailureWithNoRetry(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-secret")));
        var result=client.query("synthetic-token");
        assertThat(result.status()).isEqualTo(COMMUNICATION_ERROR);assertThat(result.methods()).isEmpty();
        assertThat(output.getAll()).doesNotContain("synthetic-secret");server.verify();
    }
    @Test void ambiguousHttpDenialDoesNotProveApiPermission() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("synthetic-secret"));
        assertThat(client.query("synthetic-token").status()).isEqualTo(COMMUNICATION_ERROR);server.verify();
    }
    @Test void malformedJsonIsNotACommunicationOrPermissionRejection() {
        server.expect(anything()).andRespond(withSuccess("<html>synthetic-token</html>",MediaType.TEXT_HTML));
        assertThat(client.query("synthetic-token").status()).isEqualTo(INVALID_RESPONSE);server.verify();
    }
    @Test void monetaryConflictsAreNeverSilentlyResolved() {
        server.expect(anything()).andRespond(withSuccess(response("["+method("CAINIAO_STANDARD","1.0","USD","1000")+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("CONFLICTING_MONETARY_FIELDS");server.verify();
    }
    @Test void missingAmountNeverDefaultsToZero() {
        server.expect(anything()).andRespond(withSuccess(response("["+method("CAINIAO_STANDARD","null","BRL",null)+"]"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").status()).isEqualTo(INVALID_RESPONSE);server.verify();
    }
    @Test void successfulFalseWithUnknownErrorRemainsInconclusive() {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_logistics_buyer_freight_calculate_response\":{\"result\":{\"success\":false,\"error_desc\":\"permission synthetic-token\"}}}",MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").status()).isEqualTo(PROVIDER_REJECTED);server.verify();
    }
}

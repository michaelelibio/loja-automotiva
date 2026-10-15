package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressProductClientTests {
    private MockRestServiceServer server;
    private AliExpressProductClient client;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressProductClient(builder.build(),"synthetic-key","synthetic-secret");
    }
    @Test
    void signedTopRequestUsesCorrectEndpointAndDoesNotLeakCredentials(CapturedOutput output) throws Exception {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    Map<String,String> params=new TreeMap<>();
                    for (String pair:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                        var parts=pair.split("=",2); params.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
                    }
                    String sign=params.remove("sign");
                    assertThat(params).containsEntry("method","aliexpress.ds.product.get").containsEntry("session","synthetic-token")
                            .containsEntry("product_id","1005008752784588").containsEntry("ship_to_country","US")
                            .containsEntry("target_currency","USD").containsEntry("target_language","en").containsEntry("v","2.0");
                    assertThat(params).doesNotContainKeys("access_token","app_secret");
                    StringBuilder input=new StringBuilder(); params.forEach((k,v)->input.append(k).append(v));
                    try {
                        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                        assertThat(sign).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
                    } catch(java.security.GeneralSecurityException ex) { throw new AssertionError(ex); }
                }).andRespond(withSuccess("{\"aliexpress_ds_product_get_response\":{\"rsp_code\":200,\"result\":{\"example\":true}}}",MediaType.APPLICATION_JSON));
        assertThat(query().path("example").asBoolean()).isTrue();
        assertThat(output.getAll()).doesNotContain("synthetic-token","synthetic-secret");server.verify();
    }
    @ParameterizedTest @CsvSource({"481,SHIP_TO_COUNTRY_CODE_NOT_VALID,COUNTRY_REJECTED","404,ITEM_ID_NOT_FOUND,NOT_FOUND","429,RATE_LIMIT,RATE_LIMIT","500,permission insufficient,PERMISSION","500,invalid token,AUTHORIZATION_REQUIRED"})
    void mapsSafeProviderErrors(String code,String message,AliExpressIntegrationException.Reason reason) {
        server.expect(anything()).andRespond(withSuccess("{\"rsp_code\":\""+code+"\",\"rsp_msg\":\""+message+" synthetic-secret\"}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(this::query).isInstanceOfSatisfying(AliExpressIntegrationException.class,e->assertThat(e.getReason()).isEqualTo(reason))
                .hasMessageNotContaining("synthetic-secret").hasNoCause();server.verify();
    }
    @Test void mapsHttpRateLimit() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("synthetic-secret"));
        assertThatThrownBy(this::query).isInstanceOfSatisfying(AliExpressIntegrationException.class,e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.RATE_LIMIT));
    }
    @Test void rejectsIncompleteResponse() {
        server.expect(anything()).andRespond(withSuccess("{\"rsp_code\":200}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(this::query).isInstanceOfSatisfying(AliExpressIntegrationException.class,e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.INCOMPLETE));
    }
    @Test void mapsTimeoutWithoutExceptionCause() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-token")));
        assertThatThrownBy(this::query).isInstanceOfSatisfying(AliExpressIntegrationException.class,e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.TIMEOUT)).hasNoCause();
    }
    @ParameterizedTest
    @MethodSource("incompleteResponses")
    void incompleteResponseDiagnosticsContainOnlyShape(String body,String stage,String field,String type,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThatThrownBy(this::query).isInstanceOfSatisfying(AliExpressIntegrationException.class,
                        e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.INCOMPLETE))
                .hasMessage("Resposta AliExpress incompleta ou invalida; importacao cancelada.").hasNoCause();
        assertThat(output.getAll()).contains("stage="+stage+"; field="+field+"; condition=expected_object; actualType="+type)
                .doesNotContain("synthetic-token","synthetic-secret","synthetic-refresh","synthetic-code","https://example.test/oauth/authorize");
        server.verify();
    }
    static java.util.stream.Stream<Arguments> incompleteResponses() {
        return java.util.stream.Stream.of(
                Arguments.of("","response_body","$","NO_BODY"),
                Arguments.of("[]","response_body","$","ARRAY"),
                Arguments.of("\"synthetic-token\"","response_body","$","STRING"),
                Arguments.of("{\"rsp_code\":200,\"access_token\":\"synthetic-token\"}","response_result","result","MISSING"),
                Arguments.of("{\"aliexpress_ds_product_get_response\":{\"rsp_code\":200,\"result\":null}}","response_result","result","NULL"),
                Arguments.of("{\"rsp_code\":200,\"result\":\"synthetic-token synthetic-secret synthetic-refresh synthetic-code https://example.test/oauth/authorize\"}","response_result","result","STRING"),
                Arguments.of("{\"rsp_code\":200,\"result\":[]}","response_result","result","ARRAY")
        );
    }
    private tools.jackson.databind.JsonNode query() { return client.get("1005008752784588","US","USD","en","synthetic-token"); }
}

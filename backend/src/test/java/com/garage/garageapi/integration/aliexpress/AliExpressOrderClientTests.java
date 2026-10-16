package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressOrderClientTests {
    MockRestServiceServer server;AliExpressOrderClient client;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressOrderClient(builder.build(),"synthetic-key","synthetic-secret",true);
    }
    @Test void signsPlaceOrderAndPreservesLargeExternalIdsWithoutLoggingSecrets(CapturedOutput output) {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(method(HttpMethod.POST)).andExpect(request->{
            var params=new TreeMap<String,String>();
            for(String pair:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                var parts=pair.split("=",2);params.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
            }
            assertThat(params).containsEntry("method","aliexpress.trade.buy.placeorder").containsEntry("session","synthetic-token");
            assertThat(params.get("param_place_order_request4_open_api_d_t_o")).contains("INGARAGE-123");
            String signature=params.remove("sign");StringBuilder canonical=new StringBuilder();params.forEach((k,v)->canonical.append(k).append(v));
            try {
                var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                assertThat(signature).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            } catch(Exception ex) {throw new AssertionError(ex);}
        }).andRespond(withSuccess("{\"aliexpress_trade_buy_placeorder_response\":{\"result\":{\"is_success\":true,\"order_list\":{\"number\":[12345678901234567,12345678901234568]}}}}",MediaType.APPLICATION_JSON));
        assertThat(query().orderIds()).containsExactly("12345678901234567","12345678901234568");server.verify();
        assertThat(output.getAll()).doesNotContain("synthetic-token","synthetic-secret","52998224725");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"error_response\":{\"msg\":\"synthetic-secret\"}}",
        "{\"aliexpress_trade_buy_placeorder_response\":{\"result\":{\"is_success\":true}}}",
        "{\"aliexpress_trade_buy_placeorder_response\":{\"result\":{\"is_success\":false,\"error_code\":\"REPEATED_ORDER_ERROR\"}}}",
        "{\"aliexpress_trade_buy_placeorder_response\":{\"result\":{\"is_success\":false,\"error_code\":\"DELIVERY_METHOD_NOT_EXIST\",\"order_list\":{\"number\":[123]}}}}"})
    void ambiguousAndRepeatOrderResponsesNeverAllowRetry(String body) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThat(query()).isEqualTo(AliExpressOrderClient.Result.unknown());server.verify();
    }
    @Test void definiteValidationRejectionIsSeparatedFromUnknown() {
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_trade_buy_placeorder_response\":{\"result\":{\"is_success\":false,\"error_code\":\"DELIVERY_METHOD_NOT_EXIST\"}}}",MediaType.APPLICATION_JSON));
        assertThat(query().rejected()).isTrue();server.verify();
    }
    @Test void timeoutNeverRetries() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-secret")));
        assertThat(query()).isEqualTo(AliExpressOrderClient.Result.unknown());server.verify();
    }
    @Test void disabledClientMakesNoHttpRequests() {
        var builder=RestClient.builder();var empty=MockRestServiceServer.bindTo(builder).build();
        assertThat(new AliExpressOrderClient(builder.build(),"key","secret",false).create(Map.of(),"token")).isEqualTo(AliExpressOrderClient.Result.unknown());empty.verify();
    }
    private AliExpressOrderClient.Result query() {return client.create(Map.of("out_order_id","INGARAGE-123"),"synthetic-token");}
}

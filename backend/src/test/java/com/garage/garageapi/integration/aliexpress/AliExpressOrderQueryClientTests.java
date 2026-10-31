package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AliExpressOrderQueryClientTests {
    @Test void lookupUsesOnlyDocumentedReadMethodAndExactStoredId() {
        var builder=RestClient.builder().baseUrl("https://eco.taobao.com");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://eco.taobao.com/router/rest")).andExpect(content().string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("method=aliexpress.ds.trade.order.get"),org.hamcrest.Matchers.containsString("order_id=12345678901234567"),
                org.hamcrest.Matchers.containsString("sign_method=hmac"),org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("placeorder")))))
                .andRespond(withSuccess("{\"aliexpress_ds_trade_order_get_response\":{\"result\":{\"order_status\":\"WAIT_SELLER_SEND_GOODS\"}}}",MediaType.APPLICATION_JSON));
        var client=new AliExpressOrderQueryClient(builder.build(),"synthetic-key","synthetic-secret",true);
        var part=client.query("12345678901234567","synthetic-token");
        assertThat(part.verified()).isTrue();assertThat(part.state()).isEqualTo("AWAITING_SHIPMENT");server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"error_response\":{\"msg\":\"private-token\"}}",
        "{\"aliexpress_ds_trade_order_get_response\":{\"rsp_code\":404,\"result\":{\"order_status\":\"WAIT_SELLER_SEND_GOODS\"}}}",
        "{\"aliexpress_ds_trade_order_get_response\":{\"result\":{\"order_status\":\"WAIT_SELLER_SEND_GOODS\",\"order_id\":999}}}",
        "{\"aliexpress_ds_trade_order_get_response\":{\"result\":{\"order_status\":\"private-token\"}}}"})
    void malformedErrorOrUnrecognizedStatusNeverProvesPayment(String body) {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var part=new AliExpressOrderQueryClient(builder.build(),"key","secret",true).query("123","token");
        assertThat(part.verified()).isFalse();assertThat(part.toString()).doesNotContain("private-token");server.verify();
    }
    @Test void timeoutDoesNotRetryOrLeakProviderDetails() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("private-token")));
        var part=new AliExpressOrderQueryClient(builder.build(),"key","secret",true).query("123","token");
        assertThat(part.state()).isEqualTo("UNKNOWN");assertThat(part.toString()).doesNotContain("private-token");server.verify();
    }
    @Test void disabledLookupDoesNotCallSupplier() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        assertThat(new AliExpressOrderQueryClient(builder.build(),"key","secret",false).query("123","token").verified()).isFalse();server.verify();
    }
    @Test void supplierStatesDoNotConfuseProcessingPartialShipmentOrClosureWithPayment() {
        assertThat(AliExpressSupplierSnapshot.classify("1","RISK_CONTROL",null).state()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(AliExpressSupplierSnapshot.classify("1","SELLER_PART_SEND_GOODS",null).state()).isEqualTo("PARTIALLY_SHIPPED");
        for(String code:new String[]{"PAYMENT_PROCESSING","WAIT_SELLER_EXAMINE_MONEY","FINISH","FUND_PROCESSING"})
            assertThat(AliExpressSupplierSnapshot.classify("1",code,null).verified()).isFalse();
        assertThat(AliExpressSupplierSnapshot.classify("1","PLACE_ORDER_SUCCESS","SELLER_SEND_GOODS").verified()).isFalse();
    }
}

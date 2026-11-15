package com.garage.garageapi.payment.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MercadoPagoPaymentSearchClientTests {
    @Test void exactReferenceSearchDiscoversIdsWithoutTrustingSearchStatus() {
        var builder=RestClient.builder().baseUrl("https://api.example.invalid");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.invalid/v1/payments/search?external_reference=garage_order_1_payment_2&sort=date_last_updated&criteria=desc&limit=50&offset=0"))
                .andExpect(header("Authorization","Bearer synthetic-token"))
                .andRespond(withSuccess("{\"paging\":{\"total\":1},\"results\":[{\"id\":123,\"external_reference\":\"garage_order_1_payment_2\",\"status\":\"approved\"}]}",MediaType.APPLICATION_JSON));
        assertThat(new MercadoPagoPaymentSearchClient(builder.build(),"synthetic-token").findIds("garage_order_1_payment_2")).containsExactly("123");server.verify();
    }
    @Test void mismatchedReferenceAndTruncatedResultsAreRejected() {
        var builder=RestClient.builder().baseUrl("https://api.example.invalid");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"results\":[{\"id\":1,\"external_reference\":\"wrong\"}]}",MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"paging\":{\"total\":51},\"results\":[]}",MediaType.APPLICATION_JSON));
        var client=new MercadoPagoPaymentSearchClient(builder.build(),"synthetic-token");
        assertThatThrownBy(()->client.findIds("garage_order_1_payment_2")).hasMessage("Referência divergente na recuperação");
        assertThatThrownBy(()->client.findIds("garage_order_1_payment_2")).isInstanceOf(MercadoPagoPaymentSearchClient.AmbiguousPaymentsException.class);
    }
}

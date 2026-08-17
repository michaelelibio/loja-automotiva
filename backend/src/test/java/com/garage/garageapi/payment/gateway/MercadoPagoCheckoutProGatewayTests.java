package com.garage.garageapi.payment.gateway;

import com.garage.garageapi.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoCheckoutProGatewayTests {
    @Test
    void createsSandboxPreferenceWithItemsShippingAndConfiguredUrls() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoCheckoutProGateway gateway = gateway(builder, true);
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TOKEN"))
                .andExpect(jsonPath("$.external_reference").value("garage_order_1_payment_2"))
                .andExpect(jsonPath("$.items[0].unit_price").value(25.00))
                .andExpect(jsonPath("$.items[1].id").value("shipping"))
                .andExpect(jsonPath("$.items[1].unit_price").value(18.90))
                .andExpect(jsonPath("$.back_urls.success").value("https://shop.example/success"))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andExpect(jsonPath("$.notification_url")
                        .value("https://api.example/webhook?source_news=webhooks"))
                .andRespond(withSuccess("{\"id\":\"PREF\",\"init_point\":\"https://prod\","
                        + "\"sandbox_init_point\":\"https://sandbox\"}", MediaType.APPLICATION_JSON));

        var result = gateway.createPreference(request());
        assertThat(result.checkoutUrl()).isEqualTo("https://sandbox");
        server.verify();
    }

    @Test
    void productionUsesInitPoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoCheckoutProGateway gateway = gateway(builder, false);
        server.expect(anything()).andRespond(withSuccess(
                "{\"id\":\"PREF\",\"init_point\":\"https://prod\","
                        + "\"sandbox_init_point\":\"https://sandbox\"}", MediaType.APPLICATION_JSON));
        assertThat(gateway.createPreference(request()).checkoutUrl()).isEqualTo("https://prod");
    }

    @Test
    void authenticatedPaymentLookupMapsOfficialFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoCheckoutProGateway gateway = gateway(builder, true);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/900"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer TOKEN"))
                .andRespond(withSuccess("{\"id\":900,\"status\":\"approved\","
                        + "\"status_detail\":\"accredited\",\"transaction_amount\":68.90,"
                        + "\"currency_id\":\"BRL\",\"external_reference\":\"ref\","
                        + "\"payment_type_id\":\"credit_card\",\"payment_method_id\":\"visa\","
                        + "\"date_approved\":\"2026-08-15T10:00:00Z\"}", MediaType.APPLICATION_JSON));
        var result = gateway.findPayment("900");
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.transactionAmount()).isEqualByComparingTo("68.90");
        assertThat(result.paymentType()).isEqualTo("credit_card");
    }

    private MercadoPagoCheckoutProGateway gateway(RestClient.Builder builder, boolean sandbox) {
        return new MercadoPagoCheckoutProGateway(builder.build(), "TOKEN",
                "https://shop.example/success", "https://shop.example/pending",
                "https://shop.example/failure", "https://api.example/webhook", sandbox);
    }

    private CheckoutProGateway.PreferenceRequest request() {
        return new CheckoutProGateway.PreferenceRequest(1L, 2L, new BigDecimal("68.90"),
                "Cliente", "buyer@example.com", "key",
                List.of(new CheckoutProGateway.Item("10", "Produto", 2, new BigDecimal("25.00"))),
                new BigDecimal("18.90"), "Entrega padrão");
    }
}

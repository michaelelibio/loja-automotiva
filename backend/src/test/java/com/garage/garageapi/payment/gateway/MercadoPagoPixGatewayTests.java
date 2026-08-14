package com.garage.garageapi.payment.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.garage.garageapi.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoPixGatewayTests {

    @Test
    void usesOfficialAutomaticApprovalPayerOnlyInSandbox() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(
                builder.build(), "TEST-TOKEN", "sandbox-buyer@testuser.com");

        expectSuccessfulRequest(server, "test_user_br@testuser.com", true);

        gateway.create(requestWithPayerEmail("real-user@example.com"));

        server.verify();
    }

    @Test
    void usesRealUserEmailWhenSandboxConfigurationIsBlank() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN", "   ");

        expectSuccessfulRequest(server, "real-user@example.com", false);

        gateway.create(requestWithPayerEmail("real-user@example.com"));

        server.verify();
    }

    @Test
    void logsSafeProviderResponseDetailsWhenDevelopmentDiagnosticsAreEnabled() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "SECRET-TOKEN", true);
        Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoPixGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        server.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andRespond(withBadRequest().body("{\"message\":\"invalid request\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        try {
            assertThatThrownBy(() -> gateway.create(new PixPaymentGateway.Request(
                    10L, 20L, new BigDecimal("19.90"), "buyer@example.com", "attempt-key")))
                    .isInstanceOf(PaymentProviderException.class)
                    .hasMessage("Falha ao criar cobrança PIX no Mercado Pago");

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("operação PIX", "endpoint=POST /v1/orders", "httpStatus=400")
                        .doesNotContain("SECRET-TOKEN", "Authorization", "attempt-key",
                                "invalid request", "responseBody");
            });
        } finally {
            logger.detachAppender(appender);
        }
        server.verify();
    }

    @Test
    void classifiesOnlyTheSpecificConflictAsAnUnusableIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");

        server.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"errors\":[{\"code\":\"idempotency_key_already_used\"}]}"));

        assertThatThrownBy(() -> gateway.create(new PixPaymentGateway.Request(
                10L, 20L, new BigDecimal("19.90"), "buyer@example.com", "attempt-key")))
                .isInstanceOfSatisfying(PaymentProviderException.class,
                        exception -> assertThat(exception.isDefinitiveRejection()).isTrue());
        server.verify();
    }

    @Test
    void classifiesHttp400ValidationResponseAsDefinitive() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");

        server.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andRespond(withBadRequest().body(
                        "{\"errors\":[{\"code\":\"invalid_email_for_sandbox\"}]}"));

        assertThatThrownBy(() -> gateway.create(new PixPaymentGateway.Request(
                10L, 20L, new BigDecimal("19.90"), "buyer@example.com", "attempt-key")))
                .isInstanceOfSatisfying(PaymentProviderException.class,
                        exception -> assertThat(exception.isDefinitiveRejection()).isTrue());
        server.verify();
    }

    @Test
    void sendsOrdersApiPixRequestAndMapsProviderResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");

        server.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-TOKEN"))
                .andExpect(header("X-Idempotency-Key", "attempt-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("online"))
                .andExpect(jsonPath("$.total_amount").value("19.90"))
                .andExpect(jsonPath("$.external_reference").value("garage_order_10_payment_20"))
                .andExpect(jsonPath("$.processing_mode").value("automatic"))
                .andExpect(jsonPath("$.payer.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.transactions.payments[0].amount").value("19.90"))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.id").value("pix"))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.type").value("bank_transfer"))
                .andRespond(withSuccess("""
                        {
                          "id": "ORD01",
                          "transactions": {
                            "payments": [{
                              "id": "PAY01",
                              "status": "action_required",
                              "expiration_time": "2026-08-14T16:00:00Z",
                              "payment_method": {
                                "qr_code": "000201...",
                                "qr_code_base64": "base64-pix"
                              }
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PixPaymentGateway.Result result = gateway.create(new PixPaymentGateway.Request(
                10L, 20L, new BigDecimal("19.90"), "buyer@example.com", "attempt-key"));

        assertThat(result.providerPaymentId()).isEqualTo("PAY01");
        assertThat(result.providerOrderId()).isEqualTo("ORD01");
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.qrCode()).isEqualTo("000201...");
        assertThat(result.qrCodeBase64()).isEqualTo("base64-pix");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-08-14T16:00:00Z"));
        assertThat(result.paidAt()).isNull();
        server.verify();
    }

    @Test
    void retrievesCurrentOrderStateFromProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/ORD01"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-TOKEN"))
                .andRespond(withSuccess("""
                        {
                          "id": "ORD01",
                          "external_reference": "garage_order_10_payment_20",
                          "status": "processed",
                          "status_detail": "accredited",
                          "last_updated_date": "2026-08-14T16:00:00Z",
                          "transactions": {
                            "payments": [{
                              "id": "PAY01",
                              "status": "processed",
                              "status_detail": "accredited",
                              "payment_method": {}
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PixPaymentGateway.Result result = gateway.find("ORD01");

        assertThat(result.providerOrderId()).isEqualTo("ORD01");
        assertThat(result.providerPaymentId()).isEqualTo("PAY01");
        assertThat(result.externalReference()).isEqualTo("garage_order_10_payment_20");
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-08-14T16:00:00Z"));
        server.verify();
    }

    @Test
    void mapsDocumentedOrderStatusesFromAuthenticatedGetResponse() {
        assertFindStatus("created", "created", PaymentStatus.PENDING);
        assertFindStatus("action_required", "waiting_transfer", PaymentStatus.PENDING);
        assertFindStatus("processing", "in_process", PaymentStatus.PENDING);
        assertFindStatus("failed", "failed", PaymentStatus.FAILED);
        assertFindStatus("canceled", "canceled", PaymentStatus.CANCELED);
        assertFindStatus("expired", "expired", PaymentStatus.EXPIRED);
    }

    @Test
    void doesNotTreatProcessedWithoutAccreditedDetailAsPaid() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");
        server.expect(requestTo("https://api.mercadopago.com/v1/orders/ORD01"))
                .andRespond(withSuccess(orderResponse("processed", "partially_refunded"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.find("ORD01"))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("sem confirma");
        server.verify();
    }

    private void assertFindStatus(String status, String detail, PaymentStatus expected) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPixGateway gateway = new MercadoPagoPixGateway(builder.build(), "TEST-TOKEN");
        server.expect(requestTo("https://api.mercadopago.com/v1/orders/ORD01"))
                .andRespond(withSuccess(orderResponse(status, detail), MediaType.APPLICATION_JSON));

        assertThat(gateway.find("ORD01").status()).isEqualTo(expected);
        server.verify();
    }

    private String orderResponse(String status, String detail) {
        return """
                {
                  "id": "ORD01",
                  "status": "%s",
                  "status_detail": "%s",
                  "transactions": {"payments": [{"id": "PAY01", "payment_method": {}}]}
                }
                """.formatted(status, detail);
    }

    private void expectSuccessfulRequest(MockRestServiceServer server, String payerEmail,
                                         boolean expectsAutomaticApproval) {
        var expectation = server.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andExpect(jsonPath("$.payer.email").value(payerEmail));
        if (expectsAutomaticApproval) {
            expectation.andExpect(jsonPath("$.payer.first_name").value("APRO"));
        } else {
            expectation.andExpect(jsonPath("$.payer.first_name").doesNotExist());
        }
        expectation
                .andRespond(withSuccess("""
                        {
                          "id": "ORD01",
                          "transactions": {
                            "payments": [{
                              "id": "PAY01",
                              "status": "pending",
                              "payment_method": {}
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private PixPaymentGateway.Request requestWithPayerEmail(String payerEmail) {
        return new PixPaymentGateway.Request(
                10L, 20L, new BigDecimal("19.90"), payerEmail, "attempt-key");
    }
}

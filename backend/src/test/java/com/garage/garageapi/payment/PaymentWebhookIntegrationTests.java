package com.garage.garageapi.payment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.PaymentProviderException;
import com.garage.garageapi.payment.gateway.PixPaymentGateway;
import com.garage.garageapi.payment.controller.MercadoPagoWebhookController;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookIntegrationTests {
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired MockMvc mockMvc;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean PixPaymentGateway pixPaymentGateway;

    @AfterEach
    void clean() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void validApprovedWebhookMarksPaymentAndOrderPaidAndDuplicateIsIdempotent() throws Exception {
        Fixture fixture = fixture("ORD-PAID", "PAY-PAID");
        PixPaymentGateway.Result approved = result(fixture, PaymentStatus.PAID);
        when(pixPaymentGateway.find("ORD-PAID")).thenReturn(approved);

        sendValid("ORD-PAID").andExpect(status().isOk());
        Instant firstUpdatedAt = paymentRepository.findById(fixture.paymentId()).orElseThrow().getUpdatedAt();
        sendValid("ORD-PAID").andExpect(status().isOk());

        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(Instant.parse("2026-08-14T16:00:00Z"));
        assertThat(payment.getUpdatedAt()).isEqualTo(firstUpdatedAt);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        verify(pixPaymentGateway, times(2)).find("ORD-PAID");
    }

    @Test
    void pendingWebhookKeepsPaymentAndOrderPending() throws Exception {
        Fixture fixture = fixture("ORD-PENDING", "PAY-PENDING");
        when(pixPaymentGateway.find("ORD-PENDING")).thenReturn(result(fixture, PaymentStatus.PENDING));

        sendValid("ORD-PENDING").andExpect(status().isOk());

        assertThat(paymentRepository.findById(fixture.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void rejectedCanceledAndExpiredWebhooksApplyCorrespondingPaymentStates() throws Exception {
        Fixture rejected = fixture("ORD-FAILED", "PAY-FAILED");
        Fixture canceled = fixture("ORD-CANCELED", "PAY-CANCELED");
        Fixture expired = fixture("ORD-EXPIRED", "PAY-EXPIRED");
        when(pixPaymentGateway.find("ORD-FAILED")).thenReturn(result(rejected, PaymentStatus.FAILED));
        when(pixPaymentGateway.find("ORD-CANCELED")).thenReturn(result(canceled, PaymentStatus.CANCELED));
        when(pixPaymentGateway.find("ORD-EXPIRED")).thenReturn(result(expired, PaymentStatus.EXPIRED));

        sendValid("ORD-FAILED").andExpect(status().isOk());
        sendValid("ORD-CANCELED").andExpect(status().isOk());
        sendValid("ORD-EXPIRED").andExpect(status().isOk());

        assertThat(paymentRepository.findById(rejected.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentRepository.findById(canceled.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        assertThat(paymentRepository.findById(expired.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(orderRepository.findById(rejected.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepository.findById(canceled.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepository.findById(expired.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void webhookSynchronizationDoesNotChangeStock() throws Exception {
        Product product = productRepository.save(new Product("Produto webhook", "produto-webhook",
                null, null, new BigDecimal("50.00"), null, "Categoria", 17,
                null, true));
        Fixture fixture = fixture("ORD-STOCK", "PAY-STOCK");
        when(pixPaymentGateway.find("ORD-STOCK"))
                .thenReturn(result(fixture, PaymentStatus.PAID));

        sendValid("ORD-STOCK").andExpect(status().isOk());
        sendValid("ORD-STOCK").andExpect(status().isOk());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(17);
    }

    @Test
    void unknownPaymentNotificationIsAcknowledgedWithoutLocalChanges() throws Exception {
        when(pixPaymentGateway.find("ORD-UNKNOWN")).thenReturn(new PixPaymentGateway.Result(
                "ORD-UNKNOWN", "PAY-UNKNOWN", null, PaymentStatus.PAID,
                null, null, null, Instant.now()));

        sendValid("ORD-UNKNOWN").andExpect(status().isOk());

        assertThat(paymentRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void temporaryProviderFailureDoesNotChangePaymentToFailed() throws Exception {
        Fixture fixture = fixture("ORD-RETRY", "PAY-RETRY");
        when(pixPaymentGateway.find("ORD-RETRY"))
                .thenThrow(new PaymentProviderException("Falha ao consultar cobrança PIX no Mercado Pago"));

        sendValid("ORD-RETRY").andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(WEBHOOK_SECRET))));

        assertThat(paymentRepository.findById(fixture.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void invalidSignatureReturnsEmptyUnauthorizedResponseWithoutProviderCall() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .queryParam("data.id", "ORD-INVALID").queryParam("type", "order")
                        .header("x-request-id", "request-invalid")
                        .header("x-signature", "ts=1,v1=invalid"))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));

        verifyNoInteractions(pixPaymentGateway);
    }

    @Test
    void missingSignatureReturnsEmptyUnauthorizedResponseWithoutRequiringJwt() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .queryParam("data.id", "ORD-MISSING").queryParam("type", "order")
                        .header("x-request-id", "request-missing"))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));

        verifyNoInteractions(pixPaymentGateway);
    }

    @Test
    void rejectionDiagnosticDoesNotLogSignatureOrSecret() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoWebhookController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String suppliedSignature = "ts=1,v1=do-not-log-this-signature";
        try {
            mockMvc.perform(post("/api/webhooks/mercadopago")
                            .queryParam("data.id", "ORD-LOG").queryParam("type", "order")
                            .header("x-request-id", "request-log")
                            .header("x-signature", suppliedSignature))
                    .andExpect(status().isUnauthorized());

            assertThat(appender.list).isNotEmpty().allSatisfy(event ->
                    assertThat(event.getFormattedMessage())
                            .doesNotContain(WEBHOOK_SECRET, suppliedSignature, "do-not-log-this-signature"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private org.springframework.test.web.servlet.ResultActions sendValid(String providerOrderId)
            throws Exception {
        String requestId = "request-" + providerOrderId;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signature(providerOrderId, requestId, timestamp);
        return mockMvc.perform(post("/api/webhooks/mercadopago")
                .queryParam("data.id", providerOrderId).queryParam("type", "order")
                .header("x-request-id", requestId).header("x-signature", signature));
    }

    private String signature(String dataId, String requestId, String timestamp) throws Exception {
        String manifest = "id:" + dataId
                + ";request-id:" + requestId
                + ";ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private PixPaymentGateway.Result result(Fixture fixture, PaymentStatus status) {
        return new PixPaymentGateway.Result(fixture.providerOrderId(), fixture.providerPaymentId(),
                "garage_order_" + fixture.orderId() + "_payment_" + fixture.paymentId(),
                status, null, null, null,
                status == PaymentStatus.PAID ? Instant.parse("2026-08-14T16:00:00Z") : null);
    }

    private Fixture fixture(String providerOrderId, String providerPaymentId) {
        User user = userRepository.save(User.local("Usuário", providerOrderId.toLowerCase() + "@example.com",
                passwordEncoder.encode("strongPass123")));
        Address address = addressRepository.save(new Address(user, "Casa", "Michael", "89229040",
                "Rua Webhook", "10", null, "Centro", "Joinville", "SC", true));
        Order order = orderRepository.saveAndFlush(new Order(user, address, new BigDecimal("50.00"),
                new BigDecimal("0.00"), Duration.ofHours(24)));
        Payment payment = paymentRepository.saveAndFlush(new Payment(order, PaymentMethod.PIX));
        payment.applyProviderResult(providerOrderId, providerPaymentId, PaymentStatus.PENDING,
                "PIX", "BASE64", Instant.now().plusSeconds(3600), null);
        paymentRepository.saveAndFlush(payment);
        return new Fixture(order.getId(), payment.getId(), providerOrderId, providerPaymentId);
    }

    private record Fixture(Long orderId, Long paymentId, String providerOrderId,
                           String providerPaymentId) { }
}

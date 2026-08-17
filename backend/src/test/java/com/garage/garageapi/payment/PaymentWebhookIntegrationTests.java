package com.garage.garageapi.payment;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.email.OrderEmailDetails;
import com.garage.garageapi.order.email.OrderEmailService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
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
    private static final String SECRET = "test-webhook-secret";
    @Autowired MockMvc mockMvc;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean CheckoutProGateway checkoutProGateway;
    @MockitoBean OrderEmailService orderEmailService;

    @AfterEach void clean() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void approvedAuthenticatedPaymentIsAppliedOnceAndPersistsProviderMethod() throws Exception {
        Fixture fixture = fixture();
        when(checkoutProGateway.findPayment("9001")).thenReturn(result(fixture, "9001",
                PaymentStatus.PAID, "BRL", new BigDecimal("50.00"), fixture.reference()));
        send("9001", "payment").andExpect(status().isOk());
        send("9001", "payment").andExpect(status().isOk());

        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getProviderPaymentType()).isEqualTo("bank_transfer");
        assertThat(payment.getProviderPaymentMethodId()).isEqualTo("pix");
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        verify(checkoutProGateway, times(2)).findPayment("9001");
        verify(orderEmailService, times(1)).sendPaymentApproved(
                org.mockito.ArgumentMatchers.any(OrderEmailDetails.class));
    }

    @Test
    void pendingRejectedCanceledAndInvalidFinancialDataNeverConfirmOrder() throws Exception {
        assertNotPaid("pending", PaymentStatus.PENDING, "BRL", "50.00", true);
        assertNotPaid("rejected", PaymentStatus.FAILED, "BRL", "50.00", false);
        assertNotPaid("canceled", PaymentStatus.CANCELED, "BRL", "50.00", false);
        assertNotPaid("wrong-amount", PaymentStatus.PAID, "BRL", "0.01", true);
        assertNotPaid("wrong-currency", PaymentStatus.PAID, "USD", "50.00", true);

        Fixture fixture = fixture();
        when(checkoutProGateway.findPayment("wrong-ref")).thenReturn(result(fixture, "wrong-ref",
                PaymentStatus.PAID, "BRL", new BigDecimal("50.00"), "invalid-reference"));
        send("wrong-ref", "payment").andExpect(status().isOk());
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void missingOrInvalidSignatureIsRejectedWithoutProviderCall() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .queryParam("data.id", "1").queryParam("type", "payment"))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .queryParam("data.id", "1").queryParam("type", "payment")
                        .header("x-request-id", "bad").header("x-signature", "ts=1,v1=bad"))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(checkoutProGateway);
    }

    @Test
    void authenticPaymentWebhookCanResolveDataIdFromOfficialJsonBody() throws Exception {
        Fixture fixture = fixture();
        when(checkoutProGateway.findPayment("9002")).thenReturn(result(fixture, "9002",
                PaymentStatus.PAID, "BRL", new BigDecimal("50.00"), fixture.reference()));
        String requestId = "request-body-9002";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"payment.updated\",\"type\":\"payment\","
                                + "\"data\":{\"id\":\"9002\"}}")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature("9002", requestId, timestamp)))
                .andExpect(status().isOk());

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void conflictingQueryAndBodyDataIdIsRejectedBeforeProviderCall() throws Exception {
        String requestId = "request-conflict";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .queryParam("data.id", "query-id")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"body-id\"}}")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature("query-id", requestId, timestamp)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(checkoutProGateway);
    }

    @Test
    void missingDataIdAndMalformedJsonAreRejectedWithoutProviderCall() throws Exception {
        String requestId = "request-without-data";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\"}")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature(null, requestId, timestamp)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{invalid-json")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature(null, requestId, timestamp)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(checkoutProGateway);
    }

    @Test
    void legacyOrderNotificationIsAcknowledgedWithoutFinancialMutation() throws Exception {
        send("legacy-order", "order").andExpect(status().isOk());
        verifyNoInteractions(checkoutProGateway);
        assertThat(paymentRepository.count()).isZero();
    }

    private void assertNotPaid(String id, PaymentStatus status, String currency, String amount,
                               boolean remainsPending) throws Exception {
        Fixture fixture = fixture();
        when(checkoutProGateway.findPayment(id)).thenReturn(result(fixture, id, status, currency,
                new BigDecimal(amount), fixture.reference()));
        send(id, "payment").andExpect(status().isOk());
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(remainsPending ? PaymentStatus.PENDING : status);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private org.springframework.test.web.servlet.ResultActions send(String dataId, String type)
            throws Exception {
        String requestId = "request-" + dataId;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return mockMvc.perform(post("/api/webhooks/mercadopago")
                .queryParam("data.id", dataId).queryParam("type", type)
                .header("x-request-id", requestId)
                .header("x-signature", signature(dataId, requestId, timestamp)));
    }

    private String signature(String dataId, String requestId, String timestamp) throws Exception {
        String manifest = (dataId == null ? "" : "id:" + dataId + ";")
                + (requestId == null ? "" : "request-id:" + requestId + ";")
                + "ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private CheckoutProGateway.PaymentResult result(Fixture fixture, String id,
            PaymentStatus status, String currency, BigDecimal amount, String reference) {
        return new CheckoutProGateway.PaymentResult(id, reference, status, "accredited", amount,
                currency, "bank_transfer", "pix",
                status == PaymentStatus.PAID ? Instant.parse("2026-08-15T10:00:00Z") : null);
    }

    private Fixture fixture() {
        String marker = java.util.UUID.randomUUID().toString();
        User user = userRepository.save(User.local("Usuário", marker + "@example.com",
                passwordEncoder.encode("strongPass123")));
        Address address = addressRepository.save(new Address(user, "Casa", "Michael", "89229040",
                "Rua Webhook", "10", null, "Centro", "Joinville", "SC", true));
        Order order = orderRepository.saveAndFlush(new Order(user, address,
                new BigDecimal("50.00"), BigDecimal.ZERO, Duration.ofHours(24)));
        Payment payment = paymentRepository.saveAndFlush(
                new Payment(order, PaymentMethod.MERCADO_PAGO));
        String reference = "garage_order_" + order.getId() + "_payment_" + payment.getId();
        payment.applyCheckoutPreference("PREF-" + payment.getId(), reference, "https://sandbox");
        paymentRepository.saveAndFlush(payment);
        return new Fixture(order.getId(), payment.getId(), reference);
    }

    private record Fixture(Long orderId, Long paymentId, String reference) { }
}

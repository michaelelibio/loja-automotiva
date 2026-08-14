package com.garage.garageapi.payment;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.gateway.PixPaymentGateway;
import com.garage.garageapi.payment.gateway.PaymentProviderException;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean PixPaymentGateway pixPaymentGateway;

    @BeforeEach
    void cleanBefore() {
        cleanDatabase();
        doAnswer(invocation -> {
            PixPaymentGateway.Request request = invocation.getArgument(0);
            if (request == null) return null;
            return new PixPaymentGateway.Result(
                    "ORD01M00743JCP0196F09SQ3AQ" + request.paymentId(),
                    "PAY01M00743JCP0196F09SQ3AQ" + request.paymentId(),
                    "garage_order_" + request.orderId() + "_payment_" + request.paymentId(),
                    PaymentStatus.PENDING, "PIX-COPIA-COLA", "BASE64-QR",
                    Instant.parse("2026-08-14T12:00:00Z"), null);
        }).when(pixPaymentGateway).create(any());
    }

    @AfterEach
    void cleanAfter() { cleanDatabase(); }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/orders/1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PIX\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/1/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    void pixPaymentPersistsProviderFieldsAndDoesNotChangeOrderOrStock() throws Exception {
        Fixture fixture = fixture("user@example.com", "Produto", "produto");
        int stockBefore = productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity();
        Order orderBefore = orderRepository.findByIdAndUserId(fixture.orderId(), fixture.user().getId()).orElseThrow();

        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(fixture.orderId()))
                .andExpect(jsonPath("$.method").value("PIX"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerPaymentId").isNotEmpty())
                .andExpect(jsonPath("$.qrCode").value("PIX-COPIA-COLA"))
                .andExpect(jsonPath("$.qrCodeBase64").value("BASE64-QR"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-14T12:00:00Z"))
                .andExpect(jsonPath("$.paidAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(stockBefore);
        Order orderAfter = orderRepository.findByIdAndUserId(fixture.orderId(), fixture.user().getId()).orElseThrow();
        assertThat(orderAfter.getTotal()).isEqualByComparingTo(orderBefore.getTotal());
        assertThat(orderAfter.getItems().get(0).getProductName()).isEqualTo("Produto");
        Payment persisted = paymentRepository
                .findAllByOrderIdOrderByCreatedAtDescIdDesc(fixture.orderId()).get(0);
        assertThat(persisted.getProviderOrderId())
                .isEqualTo("ORD01M00743JCP0196F09SQ3AQ" + persisted.getId());
        assertThat(persisted.getProviderPaymentId())
                .isEqualTo("PAY01M00743JCP0196F09SQ3AQ" + persisted.getId());
    }

    @Test
    void repeatedPostReusesPendingAttemptAndListIsIsolated() throws Exception {
        Fixture first = fixture("first@example.com", "Produto A", "produto-a");
        Fixture second = fixture("second@example.com", "Produto B", "produto-b");
        createPayment(first);
        Long repeatedId = createPayment(first);
        createPayment(second);

        mockMvc.perform(get("/api/orders/{id}/payments", first.orderId())
                        .header("Authorization", bearer(first.user())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(repeatedId))
                .andExpect(jsonPath("$[0].orderId").value(first.orderId()));
        verify(pixPaymentGateway, times(2)).create(any());
        assertThat(paymentRepository.count()).isEqualTo(2);
    }

    @Test
    void anotherUsersOrMissingOrderIsNotVisibleForCreateOrList() throws Exception {
        Fixture owner = fixture("owner@example.com", "Produto", "produto");
        User attacker = user("attacker@example.com");

        mockMvc.perform(post("/api/orders/{id}/payments", owner.orderId())
                        .header("Authorization", bearer(attacker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PIX\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{id}/payments", owner.orderId())
                        .header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/999999/payments").header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredByTimeOrderCannotReceivePaymentAndStatusIsNotChangedYet() throws Exception {
        Fixture fixture = fixture("expired-time@example.com", "Produto", "produto");
        jdbcTemplate.update("update orders set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), fixture.orderId());

        assertConflict(fixture);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus().name())
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void paidCanceledAndExpiredStatusesCannotReceivePayment() throws Exception {
        Fixture paid = fixture("paid@example.com", "Produto P", "produto-p");
        Order paidOrder = orderRepository.findById(paid.orderId()).orElseThrow();
        paidOrder.markPaid();
        orderRepository.saveAndFlush(paidOrder);
        assertConflict(paid);

        Fixture canceled = fixture("canceled@example.com", "Produto C", "produto-c");
        Order canceledOrder = orderRepository.findById(canceled.orderId()).orElseThrow();
        canceledOrder.cancel();
        orderRepository.saveAndFlush(canceledOrder);
        assertConflict(canceled);

        Fixture expired = fixture("expired@example.com", "Produto E", "produto-e");
        Order expiredOrder = orderRepository.findById(expired.orderId()).orElseThrow();
        expiredOrder.expire();
        orderRepository.saveAndFlush(expiredOrder);
        assertConflict(expired);

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void gatewayReceivesOrderTotalAndAuthenticatedUsersEmail() throws Exception {
        Fixture fixture = fixture("amount@example.com", "Produto Valor", "produto-valor");
        createPayment(fixture);

        ArgumentCaptor<PixPaymentGateway.Request> captor =
                ArgumentCaptor.forClass(PixPaymentGateway.Request.class);
        verify(pixPaymentGateway).create(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("50.00");
        assertThat(captor.getValue().payerEmail()).isEqualTo("amount@example.com");
        assertThat(captor.getValue().idempotencyKey()).isNotBlank();
    }

    @Test
    void ambiguousTransportErrorKeepsPendingAttemptAndRetryUsesSameIdempotencyKey() throws Exception {
        Fixture fixture = fixture("retry@example.com", "Produto Retry", "produto-retry");
        int stock = productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity();
        long orders = orderRepository.count();
        org.mockito.Mockito.doThrow(new PaymentProviderException("Falha externa"))
                .doReturn(new PixPaymentGateway.Result("MP-RETRY", PaymentStatus.PENDING,
                        "PIX", "BASE64", null, null))
                .when(pixPaymentGateway).create(any());

        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isBadGateway());
        createPayment(fixture);

        ArgumentCaptor<PixPaymentGateway.Request> captor =
                ArgumentCaptor.forClass(PixPaymentGateway.Request.class);
        verify(pixPaymentGateway, times(2)).create(captor.capture());
        assertThat(captor.getAllValues().get(0).idempotencyKey())
                .isEqualTo(captor.getAllValues().get(1).idempotencyKey());
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(orders);
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(stock);
    }

    @Test
    void definitiveHttp400FailureEndsAttemptAndNextActionUsesNewKey() throws Exception {
        Fixture fixture = fixture("sandbox-error@example.com", "Produto Sandbox", "produto-sandbox-error");
        when(pixPaymentGateway.create(any()))
                .thenThrow(new PaymentProviderException("Falha ao criar cobrança PIX no Mercado Pago", null,
                        PaymentProviderException.Reason.DEFINITIVE_REJECTION))
                .thenReturn(new PixPaymentGateway.Result("MP-NEW", PaymentStatus.PENDING,
                        "PIX", "BASE64", null, null));

        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isBadGateway());

        Payment rejected = paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(fixture.orderId())
                .get(0);
        String rejectedKey = rejected.getIdempotencyKey();
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Long newPaymentId = createPayment(fixture);
        Payment newAttempt = paymentRepository.findById(newPaymentId).orElseThrow();
        assertThat(newAttempt.getId()).isNotEqualTo(rejected.getId());
        assertThat(newAttempt.getIdempotencyKey()).isNotEqualTo(rejectedKey);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
    }

    @Test
    void rejectedIdempotencyKeyFailsCurrentAttemptAndNextRequestUsesANewAttempt() throws Exception {
        Fixture fixture = fixture("rejected-key@example.com", "Produto Retry", "produto-rejected-key");
        when(pixPaymentGateway.create(any()))
                .thenThrow(new PaymentProviderException("Falha ao criar cobrança PIX no Mercado Pago", null,
                        PaymentProviderException.Reason.DEFINITIVE_REJECTION))
                .thenReturn(new PixPaymentGateway.Result("MP-NEW", PaymentStatus.PENDING,
                        "PIX", "BASE64", null, null));

        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isBadGateway());

        Payment rejected = paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(fixture.orderId())
                .get(0);
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.FAILED);
        String rejectedKey = rejected.getIdempotencyKey();

        Long newPaymentId = createPayment(fixture);
        Payment accepted = paymentRepository.findById(newPaymentId).orElseThrow();
        assertThat(accepted.getId()).isNotEqualTo(rejected.getId());
        assertThat(accepted.getIdempotencyKey()).isNotEqualTo(rejectedKey);

        ArgumentCaptor<PixPaymentGateway.Request> captor =
                ArgumentCaptor.forClass(PixPaymentGateway.Request.class);
        verify(pixPaymentGateway, times(2)).create(captor.capture());
        assertThat(captor.getAllValues().get(1).idempotencyKey())
                .isNotEqualTo(captor.getAllValues().get(0).idempotencyKey());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
    }

    @Test
    void legitimateAttemptAfterFailureGetsDifferentIdempotencyKey() throws Exception {
        Fixture fixture = fixture("new-attempt@example.com", "Produto Novo", "produto-novo");
        Long firstId = createPayment(fixture);
        var first = paymentRepository.findById(firstId).orElseThrow();
        String firstKey = first.getIdempotencyKey();
        first.applyProviderResult(first.getProviderPaymentId(), PaymentStatus.FAILED,
                first.getQrCode(), first.getQrCodeBase64(), first.getExpiresAt(), null);
        paymentRepository.saveAndFlush(first);

        Long secondId = createPayment(fixture);
        String secondKey = paymentRepository.findById(secondId).orElseThrow().getIdempotencyKey();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(secondKey).isNotEqualTo(firstKey);
    }

    private void assertConflict(Fixture fixture) throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isConflict());
    }

    private Long createPayment(Fixture fixture) throws Exception {
        String response = mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));
    }

    private Fixture fixture(String email, String productName, String slug) throws Exception {
        User user = user(email);
        Address address = addressRepository.save(new Address(user, "Casa", "Michael", "89229040",
                "Rua Snapshot", "10", null, "Centro", "Joinville", "SC", true));
        Product product = productRepository.save(new Product(productName, slug, null, null,
                new BigDecimal("25.00"), null, "Categoria", 10, null, true));
        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + address.getId() + ",\"items\":[{\"productId\":"
                                + product.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));
        return new Fixture(user, product, orderId);
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private void cleanDatabase() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record Fixture(User user, Product product, Long orderId) { }
}

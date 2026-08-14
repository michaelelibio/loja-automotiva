package com.garage.garageapi.admin;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.repository.PaymentRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;
    private User customer;
    private Address address;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        userToken = register("common@example.com");
        register("admin@example.com");
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", "admin@example.com");
        adminToken = login("admin@example.com");
        customer = userRepository.findByEmailIgnoreCase("common@example.com").orElseThrow();
        address = addressRepository.save(new Address(customer, "Casa", "Cliente Garage", "01001000",
                "Praça da Sé", "100", "Apto 1", "Sé", "São Paulo", "SP", true));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void endpointsRequireAdminAndAuthentication() throws Exception {
        Order order = order(OrderStatus.PAID);

        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/{id}", order.getId())
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/orders/{id}/status", order.getId())
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("PROCESSING")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsNewestOrdersWithPaginationAndStatusFilter() throws Exception {
        Order pending = order(OrderStatus.PENDING_PAYMENT);
        Order paid = order(OrderStatus.PAID);

        mockMvc.perform(get("/api/admin/orders?page=0&size=1")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(paid.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/admin/orders?status=PENDING_PAYMENT")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending.getId()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void adminGetsOperationalDetailWithLatestSafePaymentSummary() throws Exception {
        Product product = productRepository.save(new Product("Cera Premium", "cera-premium", "desc",
                "longa", new BigDecimal("80.00"), null, "Limpeza", 17, null, true));
        Order order = new Order(customer, address, new BigDecimal("80.00"), new BigDecimal("10.00"),
                Duration.ofHours(24));
        order.addItem(new com.garage.garageapi.order.entity.OrderItem(order, product, 1,
                new BigDecimal("80.00"), new BigDecimal("80.00")));
        order = orderRepository.saveAndFlush(order);
        Payment older = new Payment(order, PaymentMethod.PIX);
        older.applyProviderResult("ORD-old", "PAY-old", PaymentStatus.PAID, "secret-qr",
                "secret-base64", Instant.now().plusSeconds(300), Instant.now());
        paymentRepository.saveAndFlush(older);
        Payment latest = new Payment(order, PaymentMethod.PIX);
        paymentRepository.saveAndFlush(latest);

        mockMvc.perform(get("/api/admin/orders/{id}", order.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.userId").value(customer.getId()))
                .andExpect(jsonPath("$.customer.name").value("Usuário"))
                .andExpect(jsonPath("$.customer.email").value("common@example.com"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Praça da Sé"))
                .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.items[0].productSlug").value("cera-premium"))
                .andExpect(jsonPath("$.total").value(90.00))
                .andExpect(jsonPath("$.payment.method").value("PIX"))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.paidAt").doesNotExist())
                .andExpect(jsonPath("$.payment.qrCode").doesNotExist())
                .andExpect(jsonPath("$.payment.providerOrderId").doesNotExist())
                .andExpect(jsonPath("$.payment.idempotencyKey").doesNotExist());
    }

    @Test
    void adminAdvancesPaidThroughDeliveredAndFillsTimestamps() throws Exception {
        Order order = order(OrderStatus.PAID);

        transition(order, "PROCESSING", 200, "PROCESSING");
        Order processing = reload(order);
        assertThat(processing.getProcessingAt()).isNotNull();

        transition(order, "SHIPPED", 200, "SHIPPED");
        Order shipped = reload(order);
        assertThat(shipped.getShippedAt()).isNotNull();

        transition(order, "DELIVERED", 200, "DELIVERED");
        Order delivered = reload(order);
        assertThat(delivered.getDeliveredAt()).isNotNull();
        assertThat(delivered.getProcessingAt()).isBeforeOrEqualTo(delivered.getShippedAt());
        assertThat(delivered.getShippedAt()).isBeforeOrEqualTo(delivered.getDeliveredAt());
    }

    @Test
    void paidCannotSkipToShipped() throws Exception {
        transition(order(OrderStatus.PAID), "SHIPPED", 409, null);
    }

    @Test
    void pendingCannotStartProcessing() throws Exception {
        transition(order(OrderStatus.PENDING_PAYMENT), "PROCESSING", 409, null);
    }

    @Test
    void deliveredCannotRegress() throws Exception {
        transition(order(OrderStatus.DELIVERED), "PROCESSING", 409, null);
    }

    @Test
    void patchCannotSetPaymentOrReservedStatuses() throws Exception {
        for (String status : new String[]{"PAID", "PENDING_PAYMENT", "EXPIRED", "CANCELED"}) {
            Order order = order(OrderStatus.PENDING_PAYMENT);
            transition(order, status, 409, null);
            assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }
    }

    @Test
    void transitionDoesNotChangePaymentOrStock() throws Exception {
        Product product = productRepository.save(new Product("Produto", "produto", "d", "l",
                new BigDecimal("50.00"), null, "Categoria", 23, null, true));
        Order order = order(OrderStatus.PAID);
        Payment payment = new Payment(order, PaymentMethod.PIX);
        Instant paidAt = Instant.parse("2026-08-14T12:00:00Z");
        payment.applyProviderResult("ORD-1", "PAY-1", PaymentStatus.PAID, null, null, null, paidAt);
        payment = paymentRepository.saveAndFlush(payment);

        transition(order, "PROCESSING", 200, "PROCESSING");

        Payment unchanged = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(unchanged.getPaidAt()).isEqualTo(paidAt);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(23);
    }

    private Order order(OrderStatus target) {
        Order order = new Order(customer, address, new BigDecimal("100.00"), BigDecimal.ZERO,
                Duration.ofHours(24));
        if (target != OrderStatus.PENDING_PAYMENT) order.markPaid();
        if (target == OrderStatus.PROCESSING || target == OrderStatus.SHIPPED
                || target == OrderStatus.DELIVERED) order.startProcessing(Instant.now());
        if (target == OrderStatus.SHIPPED || target == OrderStatus.DELIVERED) order.markShipped(Instant.now());
        if (target == OrderStatus.DELIVERED) order.markDelivered(Instant.now());
        return orderRepository.saveAndFlush(order);
    }

    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private void transition(Order order, String target, int expectedStatus, String expectedState)
            throws Exception {
        var action = mockMvc.perform(patch("/api/admin/orders/{id}/status", order.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody(target)))
                .andExpect(status().is(expectedStatus));
        if (expectedState != null) action.andExpect(jsonPath("$.status").value(expectedState));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário\",\"email\":\"" + email
                                + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return token(response);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return token(response);
    }

    private String token(String response) {
        return response.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String bearer(String token) { return "Bearer " + token; }
    private String statusBody(String status) { return "{\"status\":\"" + status + "\"}"; }

    private void cleanDatabase() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}

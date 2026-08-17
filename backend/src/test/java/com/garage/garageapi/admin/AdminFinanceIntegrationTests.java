package com.garage.garageapi.admin;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderItemRepository;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.stock.repository.StockMovementRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFinanceIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired StockMovementRepository stockMovementRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    private String adminToken;
    private String userToken;
    private User customer;
    private Address address;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        userToken = register("finance-user@example.com", "Cliente Financeiro");
        register("finance-admin@example.com", "Administrador");
        jdbcTemplate.update("update users set role='ADMIN' where email=?", "finance-admin@example.com");
        adminToken = login("finance-admin@example.com");
        customer = userRepository.findByEmailIgnoreCase("finance-user@example.com").orElseThrow();
        address = addressRepository.save(new Address(customer, "Casa", customer.getName(), "01001000",
                "Praça da Sé", "100", null, "Sé", "São Paulo", "SP", true));
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void financeRequiresAdminAndMandatoryValidPeriod() throws Exception {
        String url = "/api/admin/finance?dateFrom=2026-08-01&dateTo=2026-08-15";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("Authorization", bearer(userToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(url).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/finance").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/finance?dateFrom=2026-08-16&dateTo=2026-08-15")
                        .header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/finance?dateFrom=2025-01-01&dateTo=2026-08-15")
                        .header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    @Test
    void emptyPeriodReturnsZeroSummaryAndEveryCalendarDay() throws Exception {
        mockMvc.perform(finance("2026-08-01", "2026-08-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.revenue").value(0.00))
                .andExpect(jsonPath("$.summary.confirmedOrders").value(0))
                .andExpect(jsonPath("$.summary.averageTicket").value(0.00))
                .andExpect(jsonPath("$.summary.grossMargin").value(0.00))
                .andExpect(jsonPath("$.daily.length()").value(3))
                .andExpect(jsonPath("$.daily[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.daily[1].revenue").value(0.00))
                .andExpect(jsonPath("$.daily[2].date").value("2026-08-03"));
    }

    @Test
    void usesBusinessTimezoneDayBoundaries() throws Exception {
        Product product = product("Timezone", "timezone", "10.00", "2.00");
        order(OrderStatus.PAID, "10.00", Instant.parse("2026-08-01T02:59:59Z"), item(product, 1, "10.00"));
        order(OrderStatus.PAID, "20.00", Instant.parse("2026-08-01T03:00:00Z"), item(product, 2, "10.00"));
        order(OrderStatus.PAID, "30.00", Instant.parse("2026-08-02T02:59:59Z"), item(product, 3, "10.00"));
        order(OrderStatus.PAID, "40.00", Instant.parse("2026-08-02T03:00:00Z"), item(product, 4, "10.00"));

        mockMvc.perform(finance("2026-08-01", "2026-08-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.revenue").value(50.00))
                .andExpect(jsonPath("$.summary.confirmedOrders").value(2));
    }

    @Test
    void includesOnlyConfirmedStatusesAndCalculatesCostProfitMarginAndTicket() throws Exception {
        Product product = product("Custos", "custos", "100.00", "10.00");
        Instant time = Instant.parse("2026-08-10T12:00:00Z");
        order(OrderStatus.PENDING_PAYMENT, "900.00", time, item(product, 1, "900.00"));
        order(OrderStatus.CANCELED, "800.00", time.plusSeconds(1), item(product, 1, "800.00"));
        order(OrderStatus.EXPIRED, "700.00", time.plusSeconds(2), item(product, 1, "700.00"));
        order(OrderStatus.PAID, "100.00", time.plusSeconds(3), item(product, 2, "50.00"));
        order(OrderStatus.PROCESSING, "200.00", time.plusSeconds(4), item(product, 3, "66.67"));
        order(OrderStatus.SHIPPED, "300.00", time.plusSeconds(5), item(product, 4, "75.00"));
        order(OrderStatus.DELIVERED, "400.00", time.plusSeconds(6), item(product, 5, "80.00"));

        mockMvc.perform(finance("2026-08-10", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.revenue").value(1000.00))
                .andExpect(jsonPath("$.summary.confirmedOrders").value(4))
                .andExpect(jsonPath("$.summary.averageTicket").value(250.00))
                .andExpect(jsonPath("$.summary.knownProductCost").value(140.00))
                .andExpect(jsonPath("$.summary.grossProfit").value(860.00))
                .andExpect(jsonPath("$.summary.grossMargin").value(86.00))
                .andExpect(jsonPath("$.ordersByStatus[0].quantity").value(1))
                .andExpect(jsonPath("$.ordersByStatus[1].quantity").value(1))
                .andExpect(jsonPath("$.ordersByStatus[6].quantity").value(1));
    }

    @Test
    void orderCreationSnapshotsCostAndLaterProductChangeDoesNotRewriteHistory() throws Exception {
        Product product = product("Snapshot", "snapshot", "50.00", "12.00");
        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + address.getId() + ",\"shippingCode\":\"STANDARD\","
                                + "\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\"id\":([0-9]+).*", "$1"));
        assertThat(orderRepository.findByIdAndUserId(orderId, customer.getId())
                .orElseThrow().getItems().get(0).getUnitCost())
                .isEqualByComparingTo("12.00");

        setCost(product, "30.00");
        entityManager.clear();
        assertThat(orderRepository.findByIdAndUserId(orderId, customer.getId())
                .orElseThrow().getItems().get(0).getUnitCost())
                .isEqualByComparingTo("12.00");
        assertThat(stockMovementRepository.count()).isEqualTo(1);
    }

    @Test
    void legacyUnknownCostIsSignaledWithoutInventingCost() throws Exception {
        Product known = product("Conhecido", "conhecido", "40.00", "5.00");
        Product unknown = product("Legado", "legado", "60.00", null);
        Instant time = Instant.parse("2026-08-11T12:00:00Z");
        order(OrderStatus.PAID, "100.00", time,
                item(known, 2, "20.00"), item(unknown, 1, "60.00"));

        mockMvc.perform(finance("2026-08-11", "2026-08-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.knownProductCost").value(10.00))
                .andExpect(jsonPath("$.summary.grossProfit").value(90.00))
                .andExpect(jsonPath("$.costCoverage.complete").value(false))
                .andExpect(jsonPath("$.costCoverage.ordersWithUnknownCost").value(1))
                .andExpect(jsonPath("$.daily[0].costCoverage.complete").value(false));
    }

    @Test
    void paidAttemptsAreDeduplicatedAndSensitivePaymentFieldsNeverAppear() throws Exception {
        Product product = product("Pagamento", "pagamento", "100.00", "20.00");
        Order order = order(OrderStatus.PAID, "100.00", Instant.parse("2026-08-12T12:00:00Z"),
                item(product, 1, "100.00"));
        payment(order, PaymentStatus.FAILED, null, "FAIL");
        payment(order, PaymentStatus.PAID, Instant.parse("2026-08-12T12:05:00Z"), "PAID-1");
        payment(order, PaymentStatus.PAID, Instant.parse("2026-08-12T12:06:00Z"), "PAID-2");

        String json = mockMvc.perform(finance("2026-08-12", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethods[0].method").value("PIX"))
                .andExpect(jsonPath("$.paymentMethods[0].orders").value(1))
                .andExpect(jsonPath("$.paymentMethods[0].revenue").value(100.00))
                .andExpect(jsonPath("$.recentTransactions[0].paidAt")
                        .value("2026-08-12T12:06:00Z"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("providerPaymentId", "providerOrderId", "idempotencyKey",
                "qrCode", "PAY-2", "sensitive", "passwordHash", "googleSubject", "accessToken");
    }

    @Test
    void rankingsAggregateOnlyConfirmedSalesAndUseDeterministicOrdering() throws Exception {
        Product alpha = product("Alpha", "alpha", "10.00", "1.00");
        Product beta = product("Beta", "beta", "20.00", "2.00");
        Product gamma = product("Gamma", "gamma", "30.00", "3.00");
        Product never = product("Nunca", "nunca", "40.00", "4.00");
        Instant time = Instant.parse("2026-08-13T12:00:00Z");
        order(OrderStatus.PAID, "80.00", time, item(alpha, 4, "10.00"), item(beta, 2, "20.00"));
        order(OrderStatus.PROCESSING, "60.00", time.plusSeconds(1), item(gamma, 2, "30.00"));
        order(OrderStatus.PENDING_PAYMENT, "400.00", time.plusSeconds(2), item(never, 10, "40.00"));

        mockMvc.perform(finance("2026-08-13", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSellingProducts.length()").value(3))
                .andExpect(jsonPath("$.topSellingProducts[0].productId").value(alpha.getId()))
                .andExpect(jsonPath("$.topSellingProducts[0].quantitySold").value(4))
                .andExpect(jsonPath("$.lowestSellingProducts.length()").value(3))
                .andExpect(jsonPath("$.lowestSellingProducts[0].productId").value(beta.getId()))
                .andExpect(jsonPath("$.lowestSellingProducts[1].productId").value(gamma.getId()));
    }

    @Test
    void recentTransactionsAreLimitedToTenAndOrderedNewestFirst() throws Exception {
        Product product = product("Recente", "recente", "10.00", "1.00");
        Instant base = Instant.parse("2026-08-14T12:00:00Z");
        List<Order> orders = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            orders.add(order(OrderStatus.PAID, "10.00", base.plusSeconds(index),
                    item(product, 1, "10.00")));
        }
        mockMvc.perform(finance("2026-08-14", "2026-08-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recentTransactions.length()").value(10))
                .andExpect(jsonPath("$.recentTransactions[0].orderId").value(orders.get(10).getId()))
                .andExpect(jsonPath("$.recentTransactions[9].orderId").value(orders.get(1).getId()))
                .andExpect(jsonPath("$.recentTransactions[0].customer.email")
                        .value("finance-user@example.com"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder finance(
            String from, String to) {
        return get("/api/admin/finance").header("Authorization", bearer(adminToken))
                .param("dateFrom", from).param("dateTo", to);
    }

    private Product product(String name, String slug, String price, String cost) {
        Product product = productRepository.saveAndFlush(new Product(name, slug, null, null,
                new BigDecimal(price), null, "Categoria", 100, null, true));
        setCost(product, cost);
        return product;
    }

    private void setCost(Product product, String cost) {
        product.updateAdmin(product.getName(), product.getSlug(), product.getDescription(),
                product.getLongDescription(), product.getPrice(), product.getOldPrice(),
                cost == null ? null : new BigDecimal(cost), product.getCategory(), product.getImageUrl(),
                product.getActive(), product.getProductType(), product.getSku());
        productRepository.saveAndFlush(product);
    }

    private ItemData item(Product product, int quantity, String unitPrice) {
        return new ItemData(product, quantity, new BigDecimal(unitPrice));
    }

    private Order order(OrderStatus status, String total, Instant createdAt, ItemData... items) {
        Order order = new Order(customer, address, new BigDecimal(total), BigDecimal.ZERO,
                Duration.ofHours(24));
        for (ItemData item : items) {
            BigDecimal subtotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            order.addItem(new OrderItem(order, item.product(), item.quantity(), item.unitPrice(), subtotal));
        }
        if (status == OrderStatus.CANCELED) order.cancel();
        else if (status == OrderStatus.EXPIRED) order.expire();
        else if (status != OrderStatus.PENDING_PAYMENT) {
            order.markPaid();
            if (status == OrderStatus.PROCESSING || status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
                order.startProcessing(createdAt);
            if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) order.markShipped(createdAt);
            if (status == OrderStatus.DELIVERED) order.markDelivered(createdAt);
        }
        order = orderRepository.saveAndFlush(order);
        jdbcTemplate.update("update orders set created_at=? where id=?", createdAt, order.getId());
        entityManager.clear();
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private void payment(Order order, PaymentStatus status, Instant paidAt, String suffix) {
        Payment payment = new Payment(order, PaymentMethod.PIX);
        payment.applyProviderResult("ORDER-" + suffix, "PAY-" + suffix, status, "sensitive-qr",
                "sensitive-base64", Instant.now().plusSeconds(3600), paidAt);
        paymentRepository.saveAndFlush(payment);
    }

    private String register(String email, String name) throws Exception {
        return token(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String token(String body) { return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1"); }
    private String bearer(String token) { return "Bearer " + token; }

    private void clean() {
        stockMovementRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record ItemData(Product product, int quantity, BigDecimal unitPrice) { }
}

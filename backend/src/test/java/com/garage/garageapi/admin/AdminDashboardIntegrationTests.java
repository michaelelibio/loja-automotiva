package com.garage.garageapi.admin;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardIntegrationTests {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    private String adminToken;
    private String userToken;
    private User customer;
    private Address address;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        userToken = register("customer-dashboard@example.com");
        register("admin-dashboard@example.com");
        jdbcTemplate.update("update users set role='ADMIN' where email=?", "admin-dashboard@example.com");
        adminToken = login("admin-dashboard@example.com");
        customer = userRepository.findByEmailIgnoreCase("customer-dashboard@example.com").orElseThrow();
        address = addressRepository.save(new Address(customer, "Casa", "Cliente Dashboard", "01001000",
                "Praça da Sé", "100", null, "Sé", "São Paulo", "SP", true));
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void dashboardRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void calculatesTodayFromConfirmedStatusesAndOfficialOrderTotal() throws Exception {
        order(OrderStatus.PENDING_PAYMENT, "999.00", todayAt(8));
        order(OrderStatus.PAID, "100.00", todayAt(9));
        order(OrderStatus.PROCESSING, "200.00", todayAt(10));
        order(OrderStatus.SHIPPED, "300.00", todayAt(11));
        order(OrderStatus.DELIVERED, "400.00", todayAt(12));
        order(OrderStatus.EXPIRED, "888.00", todayAt(13));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.revenueToday").value(1000.00))
                .andExpect(jsonPath("$.summary.ordersToday").value(6))
                .andExpect(jsonPath("$.summary.averageTicketToday").value(250.00))
                .andExpect(jsonPath("$.summary.pendingPayment").value(1))
                .andExpect(jsonPath("$.summary.processing").value(1))
                .andExpect(jsonPath("$.summary.shipped").value(1))
                .andExpect(jsonPath("$.ordersByStatus[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.ordersByStatus[0].quantity").value(1))
                .andExpect(jsonPath("$.ordersByStatus[1].status").value("PAID"))
                .andExpect(jsonPath("$.ordersByStatus[1].quantity").value(1))
                .andExpect(jsonPath("$.ordersByStatus[4].status").value("DELIVERED"))
                .andExpect(jsonPath("$.ordersByStatus[4].quantity").value(1));
    }

    @Test
    void returnsZeroTicketAndSevenZeroDaysWithoutSales() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.revenueToday").value(0.00))
                .andExpect(jsonPath("$.summary.averageTicketToday").value(0.00))
                .andExpect(jsonPath("$.revenueLast7Days.length()").value(7))
                .andExpect(jsonPath("$.revenueLast7Days[*].revenue",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.comparesEqualTo(0.0))));
    }

    @Test
    void returnsFiveMostRecentOrdersWithoutSensitiveCustomerData() throws Exception {
        for (int index = 0; index < 6; index++) {
            order(OrderStatus.PAID, String.valueOf(10 + index), todayAt(8).plusSeconds(index));
        }

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentOrders.length()").value(5))
                .andExpect(jsonPath("$.recentOrders[0].total").value(15.00))
                .andExpect(jsonPath("$.recentOrders[4].total").value(11.00))
                .andExpect(jsonPath("$.recentOrders[0].customer.name").value("Usuário"))
                .andExpect(jsonPath("$.recentOrders[0].customer.email").value("customer-dashboard@example.com"))
                .andExpect(jsonPath("$.recentOrders[0].customer.passwordHash").doesNotExist());
    }

    @Test
    void returnsConfirmedRevenueForEachOfLastSevenBusinessDays() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        order(OrderStatus.PAID, "70.00", today.minusDays(6).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        order(OrderStatus.PROCESSING, "30.00", today.minusDays(2).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        order(OrderStatus.PENDING_PAYMENT, "999.00", today.minusDays(2).atTime(13, 0).atZone(BUSINESS_ZONE).toInstant());

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueLast7Days[0].date").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.revenueLast7Days[0].revenue").value(70.00))
                .andExpect(jsonPath("$.revenueLast7Days[1].revenue").value(0.00))
                .andExpect(jsonPath("$.revenueLast7Days[4].revenue").value(30.00))
                .andExpect(jsonPath("$.revenueLast7Days[6].date").value(today.toString()));
    }

    private Order order(OrderStatus status, String total, Instant createdAt) {
        Order order = new Order(customer, address, new BigDecimal(total), BigDecimal.ZERO,
                Duration.ofHours(24));
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.CANCELED && status != OrderStatus.EXPIRED) order.markPaid();
        if (status == OrderStatus.PROCESSING || status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) order.startProcessing(createdAt);
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) order.markShipped(createdAt);
        if (status == OrderStatus.DELIVERED) order.markDelivered(createdAt);
        if (status == OrderStatus.CANCELED) order.cancel();
        if (status == OrderStatus.EXPIRED) order.expire();
        order = orderRepository.saveAndFlush(order);
        jdbcTemplate.update("update orders set created_at=? where id=?", createdAt, order.getId());
        entityManager.clear();
        return order;
    }

    private Instant todayAt(int hour) {
        return LocalDate.now(BUSINESS_ZONE).atTime(hour, 0).atZone(BUSINESS_ZONE).toInstant();
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário\",\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return token(body);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return token(body);
    }

    private String token(String body) { return body.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1"); }
    private String bearer(String token) { return "Bearer " + token; }

    private void clean() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}

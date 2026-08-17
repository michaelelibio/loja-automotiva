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
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.vehicle.entity.Vehicle;
import com.garage.garageapi.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCustomerIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        userToken = register("customer-user@example.com", "Cliente Comum");
        register("customer-admin@example.com", "Administrador");
        jdbcTemplate.update("update users set role='ADMIN' where email=?", "customer-admin@example.com");
        adminToken = login("customer-admin@example.com");
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void endpointsRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/customers/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/customers/1").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void searchesNameAndEmailFiltersProviderAndOrdersAndPaginates() throws Exception {
        User maria = userRepository.save(User.local("Maria Silva", "maria.unique@example.com",
                passwordEncoder.encode("strongPass123")));
        User google = userRepository.save(User.google("Conta Google", "google.unique@example.com",
                "trusted-google-subject", null));
        Address address = address(maria);
        order(maria, address, OrderStatus.PENDING_PAYMENT, "10.00", Instant.now());

        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("search", "mArIa"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(maria.getId()));
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("search", "GOOGLE.UNIQUE@EXAMPLE.COM"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(google.getId()));
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("authProvider", "GOOGLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("hasOrders", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(maria.getId()));
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void customerWithoutOrdersHasZeroMetricsAndMissingCustomerIsNotFound() throws Exception {
        User customer = userRepository.findByEmailIgnoreCase("customer-user@example.com").orElseThrow();
        mockMvc.perform(get("/api/admin/customers/{id}", customer.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseSummary.totalOrders").value(0))
                .andExpect(jsonPath("$.purchaseSummary.confirmedOrders").value(0))
                .andExpect(jsonPath("$.purchaseSummary.totalSpent").value(0.00))
                .andExpect(jsonPath("$.purchaseSummary.averageTicket").value(0.00))
                .andExpect(jsonPath("$.purchaseSummary.lastOrderAt").doesNotExist());
        mockMvc.perform(get("/api/admin/customers/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void metricsUseExactlyConfirmedRevenueStatusesAndCorrectLastOrder() throws Exception {
        User customer = userRepository.findByEmailIgnoreCase("customer-user@example.com").orElseThrow();
        Address address = address(customer);
        Instant base = Instant.parse("2026-08-01T12:00:00Z");
        order(customer, address, OrderStatus.PENDING_PAYMENT, "900.00", base);
        order(customer, address, OrderStatus.CANCELED, "800.00", base.plusSeconds(1));
        order(customer, address, OrderStatus.EXPIRED, "700.00", base.plusSeconds(2));
        order(customer, address, OrderStatus.PAID, "10.00", base.plusSeconds(3));
        order(customer, address, OrderStatus.PROCESSING, "20.00", base.plusSeconds(4));
        order(customer, address, OrderStatus.SHIPPED, "30.00", base.plusSeconds(5));
        order(customer, address, OrderStatus.DELIVERED, "40.00", base.plusSeconds(6));

        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(adminToken))
                        .param("search", "customer-user@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalOrders").value(7))
                .andExpect(jsonPath("$.content[0].confirmedOrders").value(4))
                .andExpect(jsonPath("$.content[0].totalSpent").value(100.00))
                .andExpect(jsonPath("$.content[0].averageTicket").value(25.00))
                .andExpect(jsonPath("$.content[0].lastOrderAt").value(base.plusSeconds(6).toString()));
    }

    @Test
    void detailReturnsAddressesVehiclesPagedOrdersAndOnlySafeLatestPaymentData() throws Exception {
        User customer = userRepository.findByEmailIgnoreCase("customer-user@example.com").orElseThrow();
        Address address = address(customer);
        vehicleRepository.save(new Vehicle(customer, "Honda", "Civic", 2020, "EXL",
                "ABC1D23", true, "https://example.com/car.jpg"));
        Order older = order(customer, address, OrderStatus.PAID, "50.00",
                Instant.parse("2026-08-01T12:00:00Z"));
        Order newer = order(customer, address, OrderStatus.PROCESSING, "70.00",
                Instant.parse("2026-08-02T12:00:00Z"));
        payment(older, PaymentStatus.PENDING, null, "PAY-OLD");
        payment(newer, PaymentStatus.PENDING, null, "PAY-FIRST");
        payment(newer, PaymentStatus.PAID, Instant.parse("2026-08-02T12:05:00Z"), "PAY-LATEST");

        String response = mockMvc.perform(get("/api/admin/customers/{id}", customer.getId())
                        .header("Authorization", bearer(adminToken))
                        .param("orderPage", "0").param("orderSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Cliente Comum"))
                .andExpect(jsonPath("$.addresses[0].zipCode").value("01001000"))
                .andExpect(jsonPath("$.vehicles[0].licensePlate").value("ABC1D23"))
                .andExpect(jsonPath("$.orders.content.length()").value(1))
                .andExpect(jsonPath("$.orders.totalElements").value(2))
                .andExpect(jsonPath("$.orders.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.orders.content[0].payment.status").value("PAID"))
                .andExpect(jsonPath("$.orders.content[0].payment.method").value("PIX"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("passwordHash", "googleSubject", "idempotencyKey",
                "providerPaymentId", "providerOrderId", "qrCode", "accessToken", "trusted-google-subject",
                "PAY-LATEST");
    }

    @Test
    void orderHistoryIsDeterministicallyPaginated() throws Exception {
        User customer = userRepository.findByEmailIgnoreCase("customer-user@example.com").orElseThrow();
        Address address = address(customer);
        Instant sameTime = Instant.parse("2026-08-03T12:00:00Z");
        Order first = order(customer, address, OrderStatus.PENDING_PAYMENT, "10.00", sameTime);
        Order second = order(customer, address, OrderStatus.PENDING_PAYMENT, "20.00", sameTime);

        mockMvc.perform(get("/api/admin/customers/{id}", customer.getId())
                        .header("Authorization", bearer(adminToken)).param("orderSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orders.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.orders.totalPages").value(2));
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    private Address address(User user) {
        return addressRepository.save(new Address(user, "Casa", user.getName(), "01001000",
                "Praça da Sé", "100", "Apto 1", "Sé", "São Paulo", "SP", true));
    }

    private Order order(User user, Address address, OrderStatus status, String total, Instant createdAt) {
        Order order = new Order(user, address, new BigDecimal(total), BigDecimal.ZERO, Duration.ofHours(24));
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

    private void payment(Order order, PaymentStatus status, Instant paidAt, String providerId) {
        Payment payment = new Payment(order, PaymentMethod.PIX);
        payment.applyProviderResult("ORDER-" + providerId, providerId, status, "sensitive-qr",
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
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        vehicleRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}

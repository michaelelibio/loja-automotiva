package com.garage.garageapi.payment;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @MockitoBean CheckoutProGateway checkoutProGateway;

    @BeforeEach
    void configureGateway() {
        clean();
        when(checkoutProGateway.createPreference(any())).thenAnswer(invocation -> {
            CheckoutProGateway.PreferenceRequest request = invocation.getArgument(0);
            return new CheckoutProGateway.PreferenceResult("PREF-" + request.paymentId(),
                    "garage_order_" + request.orderId() + "_payment_" + request.paymentId(),
                    "https://sandbox.mercadopago.com/checkout/v1/redirect?pref_id=PREF-"
                            + request.paymentId());
        });
    }

    @AfterEach void cleanAfter() { clean(); }

    @Test
    void endpointsRequireAuthenticationAndLegacyPixContractIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders/1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"MERCADO_PAGO\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/1/payments")).andExpect(status().isUnauthorized());

        Fixture fixture = fixture("contract@example.com", "Produto", "produto");
        mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                        .header("Authorization", bearer(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isBadRequest());
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void createsCheckoutFromOfficialSnapshotsAndReusesPreferenceWithoutChangingOrderOrStock()
            throws Exception {
        Fixture fixture = fixture("checkout@example.com", "Produto Snapshot", "produto-snapshot");
        int stockAfterOrder = productRepository.findById(fixture.product().getId()).orElseThrow()
                .getStockQuantity();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/orders/{id}/payments", fixture.orderId())
                            .header("Authorization", bearer(fixture.user()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"method\":\"MERCADO_PAGO\",\"amount\":0.01}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.method").value("MERCADO_PAGO"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.preferenceId").isNotEmpty())
                    .andExpect(jsonPath("$.checkoutUrl").value(
                            org.hamcrest.Matchers.startsWith("https://sandbox.mercadopago.com/")))
                    .andExpect(jsonPath("$.qrCode").doesNotExist());
        }

        ArgumentCaptor<CheckoutProGateway.PreferenceRequest> captor =
                ArgumentCaptor.forClass(CheckoutProGateway.PreferenceRequest.class);
        verify(checkoutProGateway, times(1)).createPreference(captor.capture());
        CheckoutProGateway.PreferenceRequest request = captor.getValue();
        assertThat(request.total()).isEqualByComparingTo("68.90");
        assertThat(request.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Produto Snapshot");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
        });
        assertThat(request.shippingCost()).isEqualByComparingTo("18.90");
        assertThat(paymentRepository.count()).isEqualTo(1);
        Payment persisted = paymentRepository
                .findAllByOrderIdOrderByCreatedAtDescIdDesc(fixture.orderId()).get(0);
        assertThat(persisted.getMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus().name())
                .isEqualTo("PENDING_PAYMENT");
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow()
                .getStockQuantity()).isEqualTo(stockAfterOrder);
    }

    @Test
    void userCannotCreateOrListPaymentForAnotherUsersOrder() throws Exception {
        Fixture owner = fixture("owner@example.com", "Produto", "produto-owner");
        User attacker = user("attacker@example.com");
        mockMvc.perform(post("/api/orders/{id}/payments", owner.orderId())
                        .header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"MERCADO_PAGO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{id}/payments", owner.orderId())
                        .header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentClicksCreateOnlyOneActivePreference() throws Exception {
        Fixture fixture = fixture("concurrent@example.com", "Produto", "produto-concurrent");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var action = (java.util.concurrent.Callable<Integer>) () -> mockMvc.perform(
                            post("/api/orders/{id}/payments", fixture.orderId())
                                    .header("Authorization", bearer(fixture.user()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"method\":\"MERCADO_PAGO\"}"))
                    .andReturn().getResponse().getStatus();
            var first = executor.submit(action);
            var second = executor.submit(action);
            assertThat(first.get()).isEqualTo(201);
            assertThat(second.get()).isEqualTo(201);
        } finally {
            executor.shutdownNow();
        }
        verify(checkoutProGateway, times(1)).createPreference(any());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    private Fixture fixture(String email, String productName, String slug) throws Exception {
        User user = user(email);
        Address address = addressRepository.save(new Address(user, "Casa", "Michael", "89229040",
                "Rua Snapshot", "10", null, "Centro", "Joinville", "SC", true));
        Product product = productRepository.save(new Product(productName, slug, null, null,
                new BigDecimal("25.00"), null, "Categoria", 10, null, true));
        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + address.getId() + ",\"shippingCode\":\"STANDARD\","
                                + "\"items\":[{\"productId\":" + product.getId()
                                + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));
        return new Fixture(user, product, orderId);
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email,
                passwordEncoder.encode("strongPass123")));
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private void clean() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record Fixture(User user, Product product, Long orderId) { }
}

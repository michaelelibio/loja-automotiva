package com.garage.garageapi.order;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.shipping.provider.FixedShippingProvider;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanBefore() { cleanDatabase(); }

    @AfterEach
    void cleanAfter() { cleanDatabase(); }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":1,\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validOrderUsesDatabasePriceAndCalculatesTotals() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua Original", "100");
        Product product = product("Cera Premium", "cera-premium", "89.90", true);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(request(address.getId(),
                                item(product.getId(), 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.subtotal").value(179.80))
                .andExpect(jsonPath("$.shippingCost").value(18.90))
                .andExpect(jsonPath("$.total").value(198.70))
                .andExpect(jsonPath("$.shipping.code").value("STANDARD"))
                .andExpect(jsonPath("$.shipping.name").value("Entrega padrão"))
                .andExpect(jsonPath("$.shipping.price").value(18.90))
                .andExpect(jsonPath("$.shipping.estimatedDays").value(8))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Michael"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Rua Original"))
                .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.items[0].productName").value("Cera Premium"))
                .andExpect(jsonPath("$.items[0].productSlug").value("cera-premium"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(89.90))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(179.80));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(98);
        var savedOrder = orderRepository.findAll().get(0);
        assertThat(Duration.between(savedOrder.getCreatedAt(), savedOrder.getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));
    }

    @Test
    void multipleItemsWorkAndClientCannotSupplyCommercialValues() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product first = product("Produto A", "produto-a", "10.25", true);
        Product second = product("Kit B", "kit-b", "20.50", true);

        String body = "{\"addressId\":" + address.getId() + ",\"status\":\"PAID\",\"total\":0,"
                + "\"items\":[{\"productId\":" + first.getId() + ",\"quantity\":2,\"unitPrice\":0},"
                + "{\"productId\":" + second.getId() + ",\"quantity\":3}]}";
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(82.00))
                .andExpect(jsonPath("$.total").value(100.90));

        assertThat(productRepository.findById(first.getId()).orElseThrow().getStockQuantity()).isEqualTo(98);
        assertThat(productRepository.findById(second.getId()).orElseThrow().getStockQuantity()).isEqualTo(97);
    }

    @Test
    void orderRequotesSelectedShippingAndKeepsHistoricalSnapshot() throws Exception {
        User user = user("shipping-order@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Produto Frete", "produto-frete", "40.00", true);
        String body = "{\"addressId\":" + address.getId()
                + ",\"shippingCode\":\"STANDARD\",\"shippingPrice\":0,\"shippingCost\":0,"
                + "\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}";

        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(80.00))
                .andExpect(jsonPath("$.shippingCost").value(18.90))
                .andExpect(jsonPath("$.total").value(98.90))
                .andExpect(jsonPath("$.shipping.code").value("STANDARD"))
                .andExpect(jsonPath("$.shipping.name").value("Entrega padrão"))
                .andExpect(jsonPath("$.shipping.estimatedDays").value(8))
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));

        FixedShippingProvider changedProvider = new FixedShippingProvider(new BigDecimal("99.00"), 20);
        ShippingProvider.Option changed = changedProvider.quote(new ShippingProvider.Request(
                address.getZipCode(), java.util.List.of())).get(0);
        assertThat(changed.price()).isEqualByComparingTo("99.00");

        var persisted = orderRepository.findById(orderId).orElseThrow();
        assertThat(persisted.getShippingCode()).isEqualTo("STANDARD");
        assertThat(persisted.getShippingName()).isEqualTo("Entrega padrão");
        assertThat(persisted.getShippingCost()).isEqualByComparingTo("18.90");
        assertThat(persisted.getShippingEstimatedDays()).isEqualTo(8);
        assertThat(persisted.getTotal()).isEqualByComparingTo("98.90");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(98);
    }

    @Test
    void buyingExactAvailableStockLeavesZeroAndZeroStockCannotBeBought() throws Exception {
        User user = user("stock@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Última unidade", "ultima-unidade", "50.00", true, 3);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3))))
                .andExpect(status().isCreated());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Estoque insuficiente para o produto Última unidade."));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getActive()).isTrue();
    }

    @Test
    void insufficientStockRollsBackEveryProductAndDoesNotCreateOrder() throws Exception {
        User user = user("rollback@example.com");
        Address address = address(user, "Rua", "10");
        Product enough = product("Disponível", "disponivel", "10.00", true, 10);
        Product insufficient = product("Limitado", "limitado", "20.00", true, 1);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(enough.getId(), 2),
                                item(insufficient.getId(), 5))))
                .andExpect(status().isConflict());

        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(enough.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(insufficient.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void repeatedProductIsGroupedBeforeStockValidationAndCreatesSingleSnapshot() throws Exception {
        User user = user("duplicate@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Pretinho", "pretinho", "10.00", true, 7);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3), item(product.getId(), 4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(7))
                .andExpect(jsonPath("$.items[0].subtotal").value(70.00));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void repeatedProductWhoseSumExceedsStockIsRejectedWithoutNegativeStock() throws Exception {
        User user = user("duplicate-fail@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Pretinho", "pretinho", "10.00", true, 6);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3), item(product.getId(), 4))))
                .andExpect(status().isConflict());
        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(6);
    }

    @Test
    void productLookupUsesDatabaseWriteLock() throws Exception {
        Lock lock = ProductRepository.class
                .getMethod("findAllByIdInOrderByIdWithLock", java.util.List.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void invalidQuantitiesAndEmptyItemsAreRejected() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Produto", "produto", "10.00", true);

        assertBadRequest(user, request(address.getId(), item(product.getId(), 0)));
        assertBadRequest(user, request(address.getId(), item(product.getId(), -1)));
        assertBadRequest(user, "{\"addressId\":" + address.getId() + ",\"items\":[]}");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void missingOrInactiveProductRejectsWholeTransaction() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product valid = product("Ativo", "ativo", "15.00", true);
        Product inactive = product("Inativo", "inativo", "20.00", false);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(999999L, 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(inactive.getId(), 1))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(valid.getId(), 1), item(999999L, 1))))
                .andExpect(status().isNotFound());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void addressMustBelongToAuthenticatedUser() throws Exception {
        User owner = user("owner@example.com");
        User attacker = user("attacker@example.com");
        Address address = address(owner, "Segredo", "99");
        Product product = product("Produto", "produto", "10.00", true);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(999999L, item(product.getId(), 1))))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void listAndDetailAreIsolatedByAuthenticatedUser() throws Exception {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Product product = product("Produto", "produto", "10.00", true);
        Long orderA = createOrder(userA, address(userA, "Rua A", "1"), product);
        Long orderB = createOrder(userB, address(userB, "Rua B", "2"), product);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderA));
        mockMvc.perform(get("/api/orders/{id}", orderA).header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderA));
        mockMvc.perform(get("/api/orders/{id}", orderB).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addressAndProductSnapshotsSurviveLaterChanges() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua Antiga", "100");
        Product product = product("Nome Antigo", "slug-antigo", "89.90", true);
        Long orderId = createOrder(user, address, product);

        address.update("Casa", "Outro Nome", "11111111", "Rua Nova", "200", null,
                "Outro Bairro", "Outra Cidade", "PR");
        addressRepository.saveAndFlush(address);
        product.update("Nome Novo", "slug-novo", null, null, new BigDecimal("99.90"), null,
                "Categoria", null, true, product.getProductType());
        productRepository.saveAndFlush(product);

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Michael"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Rua Antiga"))
                .andExpect(jsonPath("$.shippingAddress.number").value("100"))
                .andExpect(jsonPath("$.items[0].productName").value("Nome Antigo"))
                .andExpect(jsonPath("$.items[0].productSlug").value("slug-antigo"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(89.90));
    }

    private Long createOrder(User user, Address address, Product product) throws Exception {
        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));
    }

    private void assertBadRequest(User user, String body) throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private void cleanDatabase() {
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private Address address(User user, String street, String number) {
        return addressRepository.save(new Address(user, "Casa", "Michael", "89229040", street,
                number, null, "Centro", "Joinville", "SC", true));
    }

    private Product product(String name, String slug, String price, boolean active) {
        return product(name, slug, price, active, 100);
    }

    private Product product(String name, String slug, String price, boolean active, int stockQuantity) {
        return productRepository.save(new Product(name, slug, null, null, new BigDecimal(price), null,
                "Categoria", stockQuantity, null, active));
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private String item(Long productId, int quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private String request(Long addressId, String... items) {
        return "{\"addressId\":" + addressId + ",\"items\":[" + String.join(",", items) + "]}";
    }
}

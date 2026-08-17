package com.garage.garageapi.stock;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.stock.entity.StockMovement;
import com.garage.garageapi.stock.entity.StockMovementType;
import com.garage.garageapi.stock.entity.StockReferenceType;
import com.garage.garageapi.stock.repository.StockMovementRepository;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired StockMovementRepository movementRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;
    private User customer;
    private Address address;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        userToken = register("stock-user@example.com");
        register("stock-admin@example.com");
        jdbcTemplate.update("update users set role='ADMIN' where email=?", "stock-admin@example.com");
        adminToken = login("stock-admin@example.com");
        customer = userRepository.findByEmailIgnoreCase("stock-user@example.com").orElseThrow();
        address = addressRepository.save(new Address(customer, "Casa", "Cliente", "01001000",
                "Rua", "10", null, "Centro", "São Paulo", "SP", true));
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void adminEntryAndManualOutputRecordBalancesAndPerformer() throws Exception {
        Product product = product("Produto", "produto", 5);

        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(product.getId(), "PURCHASE_ENTRY", 7, "Reposição")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.previousStock").value(5))
                .andExpect(jsonPath("$.newStock").value(12)).andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.performedBy.email").value("stock-admin@example.com"));
        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(product.getId(), "MANUAL_ADJUSTMENT_OUT", 2, "Avaria")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.previousStock").value(12))
                .andExpect(jsonPath("$.newStock").value(10));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void dropshippingRejectsManualStockAndOrderDoesNotCreateSaleMovement() throws Exception {
        Product product = product("Dropshipping", "dropshipping", 0);
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);
        productRepository.saveAndFlush(product);

        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(product.getId(), "PURCHASE_ENTRY", 5, "Entrada indevida")))
                .andExpect(status().isConflict());

        createOrder(item(product.getId(), 2));

        Product persisted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(persisted.getStockQuantity()).isZero();
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void invalidManualMovementsAreRejectedAndSecurityIsEnforced() throws Exception {
        Product product = product("Produto", "produto", 1);
        String output = movement(product.getId(), "MANUAL_ADJUSTMENT_OUT", 2, "Avaria");
        mockMvc.perform(post("/api/admin/stock/movements").contentType(MediaType.APPLICATION_JSON)
                        .content(output)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(output)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(output)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(product.getId(), "PURCHASE_ENTRY", 0, "Inválido")))
                .andExpect(status().isBadRequest());
        assertThat(movementRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isOne();
    }

    @Test
    void orderCreatesOneSalePerAggregatedProductWithOrderReferenceAndNoAdmin() throws Exception {
        Product first = product("Primeiro", "primeiro", 10);
        Product second = product("Segundo", "segundo", 8);
        String response = createOrder(item(first.getId(), 2), item(first.getId(), 1), item(second.getId(), 4));
        Long orderId = id(response);

        List<StockMovement> sales = movementRepository.findAll();
        assertThat(sales).hasSize(2).allMatch(m -> m.getType() == StockMovementType.SALE)
                .allMatch(m -> m.getReferenceType() == StockReferenceType.ORDER)
                .allMatch(m -> m.getReferenceId().equals(orderId))
                .allMatch(m -> m.getPerformedByUser() == null);
        StockMovement firstSale = sales.stream().filter(m -> m.getProduct().getId().equals(first.getId()))
                .findFirst().orElseThrow();
        assertThat(firstSale.getQuantity()).isEqualTo(3);
        assertThat(firstSale.getPreviousStock()).isEqualTo(10);
        assertThat(firstSale.getNewStock()).isEqualTo(7);
    }

    @Test
    void failedOrderRollsBackOrderStockAndLedger() throws Exception {
        Product enough = product("Disponível", "disponivel", 5);
        Product insufficient = product("Limitado", "limitado", 1);
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order(item(enough.getId(), 2), item(insufficient.getId(), 2))))
                .andExpect(status().isConflict());
        assertThat(orderRepository.count()).isZero();
        assertThat(movementRepository.count()).isZero();
        assertThat(productRepository.findById(enough.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void movementListSupportsFiltersPaginationDeterministicOrderAndSummary() throws Exception {
        Product first = product("Primeiro", "primeiro", 0);
        Product second = product("Segundo", "segundo", 0);
        createMovement(first.getId(), "PURCHASE_ENTRY", 5);
        createMovement(first.getId(), "MANUAL_ADJUSTMENT_OUT", 1);
        createMovement(second.getId(), "MANUAL_ADJUSTMENT_IN", 3);

        mockMvc.perform(get("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .param("productId", first.getId().toString()).param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("MANUAL_ADJUSTMENT_OUT"));
        mockMvc.perform(get("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .param("type", "PURCHASE_ENTRY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/stock/summary").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProducts").value(2))
                .andExpect(jsonPath("$.totalUnits").value(7))
                .andExpect(jsonPath("$.outOfStockProducts").value(0));
    }

    @Test
    void twoConcurrentOrdersNeverMakeStockNegativeOrDuplicateSale() throws Exception {
        Product product = product("Última unidade", "ultima-unidade", 1);
        List<Integer> statuses = runConcurrently(
                () -> orderStatus(item(product.getId(), 1)),
                () -> orderStatus(item(product.getId(), 1)));
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(movementRepository.findAll()).hasSize(1);
    }

    @Test
    void concurrentAdminOutputAndOrderSerializeOnSameProductLock() throws Exception {
        Product product = product("Concorrente", "concorrente", 1);
        List<Integer> statuses = runConcurrently(
                () -> orderStatus(item(product.getId(), 1)),
                () -> manualStatus(movement(product.getId(), "MANUAL_ADJUSTMENT_OUT", 1, "Avaria")));
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(movementRepository.findAll()).hasSize(1);
    }

    private void createMovement(Long productId, String type, int quantity) throws Exception {
        mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(productId, type, quantity, "Teste")))
                .andExpect(status().isCreated());
    }

    private String createOrder(String... items) throws Exception {
        return mockMvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(order(items)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private int orderStatus(String... items) throws Exception {
        return mockMvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(order(items)))
                .andReturn().getResponse().getStatus();
    }

    private int manualStatus(String body) throws Exception {
        return mockMvc.perform(post("/api/admin/stock/movements").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> runConcurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> wrapFirst = () -> { ready.countDown(); start.await(); return first.call(); };
        Callable<Integer> wrapSecond = () -> { ready.countDown(); start.await(); return second.call(); };
        try {
            Future<Integer> firstResult = executor.submit(wrapFirst);
            Future<Integer> secondResult = executor.submit(wrapSecond);
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Product product(String name, String slug, int stock) {
        return productRepository.saveAndFlush(new Product(name, slug, null, null,
                new BigDecimal("10.00"), null, "Categoria", stock, null, true));
    }

    private String register(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário\",\"email\":\"" + email
                                + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String order(String... items) {
        return "{\"addressId\":" + address.getId() + ",\"shippingCode\":\"STANDARD\",\"items\":["
                + String.join(",", items) + "]}";
    }

    private String item(Long productId, int quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private String movement(Long productId, String type, int quantity, String reason) {
        return "{\"productId\":" + productId + ",\"type\":\"" + type + "\",\"quantity\":"
                + quantity + ",\"reason\":\"" + reason + "\"}";
    }

    private Long id(String body) { return Long.valueOf(body.replaceAll(".*\"id\":([0-9]+).*", "$1")); }
    private String token(String body) { return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1"); }
    private String bearer(String token) { return "Bearer " + token; }

    private void clean() {
        movementRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}

package com.garage.garageapi.admin;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        userToken = register("product-user@example.com");
        register("product-admin@example.com");
        jdbcTemplate.update("update users set role='ADMIN' where email=?", "product-admin@example.com");
        adminToken = login("product-admin@example.com");
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void endpointsRequireAdminAndOldPublicMutationIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson("SKU-1", "produto", "10.00", "2.00", 1, true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminCreatesListsAndGetsProductWithCostAndTimestamps() throws Exception {
        String response = create(productJson("von-vfloc-500", "v-floc-500ml", "39.90", "22.00", 10, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("VON-VFLOC-500"))
                .andExpect(jsonPath("$.costPrice").value(22.00))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long id = id(response);

        mockMvc.perform(get("/api/admin/products/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Produto Admin"));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCaseAndInvalidCommercialValues() throws Exception {
        create(productJson("SKU-DUP", "produto-a", "10.00", "1.00", 2, true))
                .andExpect(status().isCreated());
        create(productJson("sku-dup", "produto-b", "10.00", "1.00", 2, true))
                .andExpect(status().isConflict());
        create(productJson("SKU-PRICE", "produto-c", "0.00", "1.00", 2, true))
                .andExpect(status().isBadRequest());
        create(productJson("SKU-COST", "produto-d", "10.00", "-0.01", 2, true))
                .andExpect(status().isBadRequest());
        create(productJson("SKU-STOCK", "produto-e", "10.00", "1.00", -1, true))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUpdatesAndDeactivatesWhilePublicCatalogHidesProductAndCost() throws Exception {
        Long id = id(create(productJson("SKU-EDIT", "produto-edit", "20.00", "8.00", 5, true))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(put("/api/admin/products/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("SKU-EDIT-2", "produto-editado", "35.00", "12.00", 9, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(35.00))
                .andExpect(jsonPath("$.costPrice").value(12.00)).andExpect(jsonPath("$.stock").value(5));

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(35.00))
                .andExpect(jsonPath("$.costPrice").doesNotExist());
        mockMvc.perform(patch("/api/admin/products/{id}/active", id)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchFiltersAndPaginationRunInDatabase() throws Exception {
        create(productJson("AAA-001", "cera-premium", "10.00", "1.00", 1, true));
        create(productJson("BBB-002", "shampoo", "20.00", "2.00", 2, false));
        create(productJson("CCC-003", "pneu", "30.00", "3.00", 3, true));

        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/admin/products?search=aaa").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("AAA-001"));
        mockMvc.perform(get("/api/admin/products?active=false").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("BBB-002"));
        mockMvc.perform(get("/api/admin/products?category=limpeza").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/admin/products?search=aaa&active=true")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("AAA-001"));
        mockMvc.perform(get("/api/admin/products?search=bbb&category=LIMPEZA")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("BBB-002"));
        mockMvc.perform(get("/api/admin/products?search=bbb&active=false&category=LIMPEZA")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("BBB-002"));
        mockMvc.perform(get("/api/admin/products").param("search", "   ")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/admin/products?search=cCc-003").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("CCC-003"));
        mockMvc.perform(get("/api/admin/products?page=0&size=2").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("CCC-003"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void priceUpdateDoesNotChangeOrderItemSnapshotAndStockRemainsCompatible() throws Exception {
        User customer = userRepository.findByEmailIgnoreCase("product-user@example.com").orElseThrow();
        Address address = addressRepository.save(new Address(customer, "Casa", "Cliente", "01001000",
                "Rua", "10", null, "Centro", "São Paulo", "SP", true));
        Long productId = id(create(productJson("SKU-ORDER", "produto-order", "50.00", "20.00", 4, true))
                .andReturn().getResponse().getContentAsString());

        String orderResponse = mockMvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + address.getId() + ",\"shippingCode\":\"STANDARD\",\"items\":[{\"productId\":" + productId + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].unitPrice").value(50.00))
                .andReturn().getResponse().getContentAsString();
        Long orderId = id(orderResponse);
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(2);

        mockMvc.perform(put("/api/admin/products/{id}", productId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("SKU-ORDER", "produto-order", "75.00", "25.00", 8, true)))
                .andExpect(status().isOk());
        Order order = orderRepository.findByIdAndUserId(orderId, customer.getId()).orElseThrow();
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/products/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/products/999999").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("SKU-MISSING", "missing", "10.00", "1.00", 1, true)))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/admin/products").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String productJson(String sku, String slug, String price, String cost, int stock, boolean active) {
        return "{\"name\":\"Produto Admin\",\"slug\":\"" + slug + "\",\"description\":\"Descrição\"," +
                "\"price\":" + price + ",\"costPrice\":" + cost + ",\"stock\":" + stock +
                ",\"active\":" + active + ",\"category\":\"LIMPEZA\",\"sku\":\"" + sku + "\"}";
    }

    private String register(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário\",\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Long id(String body) { return Long.valueOf(body.replaceAll(".*\"id\":([0-9]+).*", "$1")); }
    private String token(String body) { return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1"); }
    private String bearer(String token) { return "Bearer " + token; }

    private void clean() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}

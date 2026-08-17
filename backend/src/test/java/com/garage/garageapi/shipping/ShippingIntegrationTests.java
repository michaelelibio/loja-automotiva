package com.garage.garageapi.shipping;

import com.garage.garageapi.auth.service.JwtService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShippingIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.local("Cliente", "shipping@example.com",
                passwordEncoder.encode("strongPass123")));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void validQuoteUsesConfiguredFixedProvider() throws Exception {
        Product product = product("Produto", "produto", "35.90", true, 10);

        quote("89229-030", "[{\"productId\":" + product.getId() + ",\"quantity\":2}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.options[0].code").value("STANDARD"))
                .andExpect(jsonPath("$.options[0].name").value("Entrega padrão"))
                .andExpect(jsonPath("$.options[0].price").value(18.90))
                .andExpect(jsonPath("$.options[0].estimatedDays").value(8));
    }

    @Test
    void invalidZipCodeIsRejected() throws Exception {
        Product product = product("Produto", "produto", "10.00", true, 10);
        quote("123", "[{\"productId\":" + product.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.zipCode").exists());
    }

    @Test
    void missingProductIsRejected() throws Exception {
        quote("89229030", "[{\"productId\":999999,\"quantity\":1}]")
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidQuantityIsRejected() throws Exception {
        Product product = product("Produto", "produto", "10.00", true, 10);
        quote("89229030", "[{\"productId\":" + product.getId() + ",\"quantity\":0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
    }

    @Test
    void multipleItemsAreValidatedAndClientCommercialValuesAreIgnored() throws Exception {
        Product first = product("Produto A", "produto-a", "999.90", true, 10);
        Product second = product("Produto B", "produto-b", "0.01", true, 10);
        String items = "[{\"productId\":" + first.getId()
                + ",\"quantity\":2,\"price\":0,\"subtotal\":0},{\"productId\":"
                + second.getId() + ",\"quantity\":3,\"price\":100000}]";

        quote("89229-030", items)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].price").value(18.90));
    }

    @Test
    void inactiveProductFollowsCurrentCatalogRule() throws Exception {
        Product product = product("Inativo", "inativo", "10.00", false, 10);
        quote("89229030", "[{\"productId\":" + product.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions quote(String zipCode, String items)
            throws Exception {
        return mockMvc.perform(post("/api/shipping/quote")
                .header("Authorization", "Bearer " + jwtService.issue(user).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zipCode\":\"" + zipCode + "\",\"items\":" + items + "}"));
    }

    private Product product(String name, String slug, String price, boolean active, int stock) {
        return productRepository.save(new Product(name, slug, null, null, new BigDecimal(price),
                null, "Categoria", stock, null, active));
    }
}

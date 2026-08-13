package com.garage.garageapi.favorite;

import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.favorite.repository.FavoriteRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteIntegrationTests {
    private static final String BASE_URL = "/api/users/me/favorites";

    @Autowired MockMvc mockMvc;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        favoriteRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authenticatedUserAddsListsChecksCountsAndRemovesFavorite() throws Exception {
        User user = user("user@example.com");
        Product product = product("Capacete", "capacete", true);
        String authorization = bearer(user);

        mockMvc.perform(post(BASE_URL + "/" + product.getId()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()));
        mockMvc.perform(get(BASE_URL).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(product.getId()))
                .andExpect(jsonPath("$[0].name").value("Capacete"))
                .andExpect(jsonPath("$[0].productType").value("SINGLE"));
        mockMvc.perform(get(BASE_URL + "/" + product.getId() + "/status").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
        mockMvc.perform(get(BASE_URL + "/count").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(delete(BASE_URL + "/" + product.getId()).header("Authorization", authorization))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE_URL + "/" + product.getId()).header("Authorization", authorization))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE_URL + "/" + product.getId() + "/status").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(false));
        mockMvc.perform(get(BASE_URL + "/count").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void addingTwiceIsIdempotent() throws Exception {
        User user = user("user@example.com");
        Product product = product("Jaqueta", "jaqueta", true);
        String authorization = bearer(user);

        mockMvc.perform(post(BASE_URL + "/" + product.getId()).header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_URL + "/" + product.getId()).header("Authorization", authorization))
                .andExpect(status().isOk());

        assertThat(favoriteRepository.countByUserId(user.getId())).isEqualTo(1);
    }

    @Test
    void usersFavoritesAreIsolated() throws Exception {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Product product = product("Luva", "luva", true);

        mockMvc.perform(post(BASE_URL + "/" + product.getId()).header("Authorization", bearer(userA)))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/" + product.getId() + "/status")
                        .header("Authorization", bearer(userB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(false));
        mockMvc.perform(delete(BASE_URL + "/" + product.getId()).header("Authorization", bearer(userB)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE_URL + "/count").header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void endpointsRequireJwtAndMissingOrInactiveProductIsRejected() throws Exception {
        User user = user("user@example.com");
        String authorization = bearer(user);

        mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_URL + "/999999").header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Produto não encontrado: 999999"));
        Product inactive = product("Bota", "bota", false);
        mockMvc.perform(post(BASE_URL + "/" + inactive.getId()).header("Authorization", authorization))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicProductGetContinuesWorking() throws Exception {
        Product product = product("Retrovisor", "retrovisor", true);
        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("retrovisor"))
                .andExpect(jsonPath("$.productType").value("SINGLE"));
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private Product product(String name, String slug, boolean active) {
        return productRepository.save(new Product(name, slug, "Descrição", "Descrição longa",
                new BigDecimal("99.90"), null, "Acessórios", 10, "https://example.com/image.jpg", active));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).value();
    }
}

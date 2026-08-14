package com.garage.garageapi.admin;

import com.garage.garageapi.user.entity.UserRole;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void adminHealthRequiresAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isUnauthorized());

        String userToken = register("user@example.com", "ADMIN");
        mockMvc.perform(get("/api/admin/health")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?",
                "user@example.com");
        String adminToken = login("user@example.com");
        assertThat(jwtDecoder.decode(adminToken).getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
        mockMvc.perform(get("/api/admin/health")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void publicRegistrationCannotChooseAdminAndUserJwtStillWorks() throws Exception {
        register("normal@example.com", "ADMIN");
        String token = login("normal@example.com");

        assertThat(userRepository.findByEmailIgnoreCase("normal@example.com").orElseThrow().getRole())
                .isEqualTo(UserRole.USER);
        assertThat(jwtDecoder.decode(token).getClaimAsStringList("roles"))
                .containsExactly("USER");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("normal@example.com"));
    }

    @Test
    void mercadoPagoWebhookRemainsOutsideJwtAuthentication() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    private String register(String email, String attemptedRole) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"UsuÃ¡rio","email":"%s","password":"strongPass123",
                                 "role":"%s"}
                                """.formatted(email, attemptedRole)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return token(response);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"strongPass123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return token(response);
    }

    private String token(String response) {
        return response.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}

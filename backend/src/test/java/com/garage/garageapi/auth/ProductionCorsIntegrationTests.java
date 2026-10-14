package com.garage.garageapi.auth;

import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {
        "app.security.frontend-url=https://ingarage.com.br",
        "app.security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:${app.security.frontend-url:http://localhost:3000}}",
        "CORS_ALLOWED_ORIGINS= https://ingarage.com.br , https://www.ingarage.com.br "
})
@AutoConfigureMockMvc
class ProductionCorsIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://ingarage.com.br", "https://www.ingarage.com.br"})
    void loginPreflightAllowsBothOfficialOriginsWithoutJwt(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/login").header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://ingarage.com.br", "https://www.ingarage.com.br"})
    void loginSucceedsWithCorsHeadersForBothOrigins(String origin) throws Exception {
        userRepository.save(User.local("CORS Test", "cors@example.test", passwordEncoder.encode("syntheticPassword123")));
        mockMvc.perform(post("/api/auth/login").header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cors@example.test\",\"password\":\"syntheticPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void unknownOriginIsRejectedOnPreflightAndActualLogin() throws Exception {
        mockMvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(post("/api/auth/login").header("Origin", "https://untrusted.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void allowedOriginDoesNotBypassAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Origin", "https://www.ingarage.com.br"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.ingarage.com.br"));
    }
}

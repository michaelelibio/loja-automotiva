package com.garage.garageapi.user;

import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserAccountIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void authenticatedUserCanReadOwnAccount() throws Exception {
        User user = saveLocalUser("Michael", "michael@example.com");
        User anotherUser = saveLocalUser("Outra Pessoa", "outra@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", bearerToken(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Michael"))
                .andExpect(jsonPath("$.email").value("michael@example.com"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(user.getId()).isNotEqualTo(anotherUser.getId());
    }

    @Test
    void authenticatedUserUpdatesOnlyOwnNameAndWhitespaceIsNormalized() throws Exception {
        User user = saveLocalUser("Michael", "michael@example.com");
        User anotherUser = saveLocalUser("Outra Pessoa", "outra@example.com");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearerToken(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Michael   Elibio  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Michael Elibio"))
                .andExpect(jsonPath("$.email").value("michael@example.com"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("Michael Elibio");
        assertThat(userRepository.findById(anotherUser.getId()).orElseThrow().getName()).isEqualTo("Outra Pessoa");
    }

    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Michael\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void invalidNameIsRejectedAndNotPersisted() throws Exception {
        User user = saveLocalUser("Michael", "michael@example.com");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearerToken(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("Michael");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearerToken(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  M  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void validJwtForMissingUserIsRejected() throws Exception {
        User user = saveLocalUser("Michael", "michael@example.com");
        String authorization = bearerToken(user.getEmail());
        userRepository.delete(user);

        mockMvc.perform(get("/api/users/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuário autenticado não encontrado"));
    }

    private User saveLocalUser(String name, String email) {
        return userRepository.save(User.local(name, email, passwordEncoder.encode("strongPass123")));
    }

    private String bearerToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = response.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        return "Bearer " + token;
    }
}

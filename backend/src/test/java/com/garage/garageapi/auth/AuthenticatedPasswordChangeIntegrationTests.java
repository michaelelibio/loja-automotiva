package com.garage.garageapi.auth;

import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.auth.token.AccountToken;
import com.garage.garageapi.auth.token.AccountTokenRepository;
import com.garage.garageapi.auth.token.AccountTokenType;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticatedPasswordChangeIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountTokenRepository tokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.local("Cliente", "cliente@example.com",
                passwordEncoder.encode("oldPassword123")));
        token = jwtService.issue(user).value();
    }

    @Test
    void authenticatedLocalUserChangesPasswordAndReceivesSanitizedResponse() throws Exception {
        String response = change(token, "oldPassword123", "newPassword123", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha alterada com sucesso."))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("oldPassword123").doesNotContain("newPassword123")
                .doesNotContain("passwordHash");
    }

    @Test
    void incorrectCurrentPasswordIsRejectedWithoutChangingHash() throws Exception {
        String originalHash = user.getPasswordHash();
        change(token, "wrongPassword", "newPassword123", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Senha atual incorreta"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);
    }

    @Test
    void unauthenticatedRequestReceives401() throws Exception {
        change(null, "oldPassword123", "newPassword123", "")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidNewPasswordIsRejectedByBackend() throws Exception {
        change(token, "oldPassword123", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
        change(token, "oldPassword123", "x".repeat(73), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void persistedPasswordIsANewBcryptHashAndNeverPlainText() throws Exception {
        String originalHash = user.getPasswordHash();
        change(token, "oldPassword123", "newPassword123", "").andExpect(status().isOk());
        String changedHash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(changedHash).isNotEqualTo(originalHash).isNotEqualTo("newPassword123");
        assertThat(passwordEncoder.matches("newPassword123", changedHash)).isTrue();
    }

    @Test
    void oldPasswordStopsAuthenticating() throws Exception {
        change(token, "oldPassword123", "newPassword123", "").andExpect(status().isOk());
        login("cliente@example.com", "oldPassword123").andExpect(status().isUnauthorized());
    }

    @Test
    void newPasswordAuthenticatesAfterChange() throws Exception {
        change(token, "oldPassword123", "newPassword123", "").andExpect(status().isOk());
        login("cliente@example.com", "newPassword123").andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCannotChangeAnotherUsersPassword() throws Exception {
        User other = userRepository.save(User.local("Outra", "outra@example.com",
                passwordEncoder.encode("otherPassword123")));
        change(token, "oldPassword123", "newPassword123", ",\"userId\":" + other.getId())
                .andExpect(status().isOk());
        assertThat(passwordEncoder.matches("otherPassword123",
                userRepository.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void userIdInRequestCannotSelectTheTargetAccount() throws Exception {
        User other = userRepository.save(User.local("Outra", "outra2@example.com",
                passwordEncoder.encode("otherPassword123")));
        change(token, "otherPassword123", "attackerPassword123", ",\"userId\":" + other.getId())
                .andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches("otherPassword123",
                userRepository.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void successfulChangeInvalidatesPendingResetTokens() throws Exception {
        AccountToken reset = tokenRepository.save(new AccountToken(user, AccountTokenType.PASSWORD_RESET,
                "a".repeat(64), Instant.now().plusSeconds(900), Instant.now()));
        change(token, "oldPassword123", "newPassword123", "").andExpect(status().isOk());
        assertThat(tokenRepository.findById(reset.getId()).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void googleAccountCannotCreateALocalPasswordThroughThisEndpoint() throws Exception {
        User google = userRepository.save(User.google("Google", "google@example.com", "subject", null));
        change(jwtService.issue(google).value(), "anything", "newPassword123", "")
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(google.getId()).orElseThrow().getPasswordHash()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions change(
            String bearer, String currentPassword, String newPassword, String extra) throws Exception {
        var request = put("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\""
                        + newPassword + "\"" + extra + "}");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}

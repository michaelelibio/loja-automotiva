package com.garage.garageapi.auth;

import com.garage.garageapi.auth.email.DevelopmentAccountEmailService;
import com.garage.garageapi.auth.service.GoogleTokenValidator;
import com.garage.garageapi.auth.token.AccountToken;
import com.garage.garageapi.auth.token.AccountTokenRepository;
import com.garage.garageapi.auth.token.AccountTokenType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountTokenRepository tokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DevelopmentAccountEmailService emailService;
    @MockitoBean GoogleTokenValidator googleTokenValidator;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        emailService.clear();
    }

    @AfterEach
    void tearDown() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        emailService.clear();
    }

    @Test
    void localRegistrationIsUnverifiedAndPersistsOnlyVerificationTokenHash() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente","email":"cliente@example.com",
                                 "password":"strongPass123","emailVerified":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString();

        User user = userRepository.findByEmailIgnoreCase("cliente@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        String rawToken = verificationToken("cliente@example.com");
        AccountToken stored = tokenRepository.findAll().get(0);
        assertThat(stored.getType()).isEqualTo(AccountTokenType.EMAIL_VERIFICATION);
        assertThat(stored.getTokenHash()).hasSize(64).isEqualTo(hash(rawToken));
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getExpiresAt()).isAfter(stored.getCreatedAt());
        assertThat(response).doesNotContain(rawToken).doesNotContain(stored.getTokenHash());
    }

    @Test
    void validVerificationMarksEmailVerifiedAndRepeatedConfirmationIsIdempotent() throws Exception {
        register("verified@example.com");
        String token = verificationToken("verified@example.com");

        verify(token).andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase("verified@example.com").orElseThrow()
                .isEmailVerified()).isTrue();
        assertThat(tokenRepository.findAll().get(0).getConsumedAt()).isNotNull();

        verify(token).andExpect(status().isOk());
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        User user = userRepository.save(User.local("Cliente", "expired@example.com",
                passwordEncoder.encode("strongPass123")));
        String rawToken = "expired-verification-token";
        tokenRepository.save(new AccountToken(user, AccountTokenType.EMAIL_VERIFICATION,
                hash(rawToken), Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600)));

        verify(rawToken).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void trustedGoogleIdentityCreatesVerifiedAccount() throws Exception {
        when(googleTokenValidator.validate("trusted-google-token"))
                .thenReturn(new GoogleTokenValidator.GoogleIdentity("google-subject",
                        "google@example.com", "Google User", null));

        mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"trusted-google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.emailVerified").value(true));

        assertThat(userRepository.findByEmailIgnoreCase("google@example.com").orElseThrow()
                .isEmailVerified()).isTrue();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void forgotPasswordResponseDoesNotRevealWhetherLocalAccountExists() throws Exception {
        userRepository.save(User.local("Cliente", "exists@example.com",
                passwordEncoder.encode("strongPass123")));

        String existing = forgot("exists@example.com");
        String missing = forgot("missing@example.com");

        assertThat(existing).isEqualTo(missing);
        assertThat(emailService.lastPasswordResetFor("exists@example.com")).isNotNull();
        assertThat(emailService.lastPasswordResetFor("missing@example.com")).isNull();
    }

    @Test
    void forgotPasswordDoesNotIssueLocalPasswordForGoogleOnlyAccount() throws Exception {
        userRepository.save(User.google("Google", "google@example.com", "subject", null));

        forgot("google@example.com");

        assertThat(emailService.lastPasswordResetFor("google@example.com")).isNull();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void validResetChangesPasswordConsumesTokenAndNeverPersistsRawToken() throws Exception {
        userRepository.save(User.local("Cliente", "reset@example.com",
                passwordEncoder.encode("oldPassword123")));
        forgot("reset@example.com");
        String rawToken = resetToken("reset@example.com");
        AccountToken stored = tokenRepository.findAll().get(0);
        assertThat(stored.getTokenHash()).isEqualTo(hash(rawToken)).isNotEqualTo(rawToken);

        reset(rawToken, "newPassword123").andExpect(status().isOk());
        login("reset@example.com", "oldPassword123").andExpect(status().isUnauthorized());
        login("reset@example.com", "newPassword123").andExpect(status().isOk());
        reset(rawToken, "anotherPassword123").andExpect(status().isBadRequest());

        assertThat(tokenRepository.findById(stored.getId()).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void expiredPasswordResetTokenIsRejected() throws Exception {
        User user = userRepository.save(User.local("Cliente", "expired-reset@example.com",
                passwordEncoder.encode("oldPassword123")));
        String rawToken = "expired-password-reset-token";
        tokenRepository.save(new AccountToken(user, AccountTokenType.PASSWORD_RESET,
                hash(rawToken), Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600)));

        reset(rawToken, "newPassword123").andExpect(status().isBadRequest());
        login("expired-reset@example.com", "oldPassword123").andExpect(status().isOk());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private String forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String verificationToken(String email) {
        return tokenFrom(emailService.lastVerificationFor(email).url());
    }

    private String resetToken(String email) {
        return tokenFrom(emailService.lastPasswordResetFor(email).url());
    }

    private String tokenFrom(String url) { return url.substring(url.indexOf("token=") + 6); }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}

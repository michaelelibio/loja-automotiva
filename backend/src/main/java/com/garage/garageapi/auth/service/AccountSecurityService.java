package com.garage.garageapi.auth.service;

import com.garage.garageapi.auth.email.AccountEmailService;
import com.garage.garageapi.auth.exception.InvalidAccountTokenException;
import com.garage.garageapi.auth.exception.InvalidCurrentPasswordException;
import com.garage.garageapi.auth.exception.AccountEmailDeliveryException;
import com.garage.garageapi.auth.token.AccountToken;
import com.garage.garageapi.auth.token.AccountTokenRepository;
import com.garage.garageapi.auth.token.AccountTokenType;
import com.garage.garageapi.user.entity.AuthProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AccountSecurityService {
    public static final String FORGOT_RESPONSE =
            "Se existir uma conta local válida, enviaremos as instruções para o e-mail informado.";

    private final AccountTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailService emailService;
    private final Duration verificationExpiration;
    private final Duration resetExpiration;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public AccountSecurityService(
            AccountTokenRepository tokenRepository,
            UserRepository userRepository,
            UserService userService,
            PasswordEncoder passwordEncoder,
            AccountEmailService emailService,
            @Value("${app.account.email-verification-expiration:PT24H}") Duration verificationExpiration,
            @Value("${app.account.password-reset-expiration:PT15M}") Duration resetExpiration,
            @Value("${app.security.frontend-url}") String frontendBaseUrl) {
        this(tokenRepository, userRepository, userService, passwordEncoder, emailService,
                verificationExpiration, resetExpiration, frontendBaseUrl,
                new SecureRandom(), Clock.systemUTC());
    }

    AccountSecurityService(AccountTokenRepository tokenRepository, UserRepository userRepository,
                           UserService userService,
                           PasswordEncoder passwordEncoder, AccountEmailService emailService,
                           Duration verificationExpiration, Duration resetExpiration,
                           String frontendBaseUrl, SecureRandom secureRandom, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.verificationExpiration = verificationExpiration;
        this.resetExpiration = resetExpiration;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public void sendVerification(User user) {
        if (user.isEmailVerified()) return;
        String rawToken = issue(user, AccountTokenType.EMAIL_VERIFICATION, verificationExpiration);
        emailService.sendVerificationEmail(user.getEmail(),
                frontendBaseUrl + "/verify-email?token=" + rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AccountToken token = find(rawToken, AccountTokenType.EMAIL_VERIFICATION);
        if (token.isConsumed()) {
            if (token.getUser().isEmailVerified()) return;
            throw invalidVerification();
        }
        Instant now = clock.instant();
        if (token.isExpired(now)) throw invalidVerification();
        token.getUser().verifyEmail();
        token.consume(now);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isActive)
                .filter(user -> user.getAuthProvider() == AuthProvider.LOCAL)
                .filter(user -> user.getPasswordHash() != null)
                .ifPresent(user -> {
                    String rawToken = issue(user, AccountTokenType.PASSWORD_RESET, resetExpiration);
                    try {
                        emailService.sendPasswordResetEmail(user.getEmail(),
                                frontendBaseUrl + "/reset-password?token=" + rawToken);
                    } catch (AccountEmailDeliveryException ignored) {
                        // A resposta precisa permanecer neutra para impedir enumeração de contas.
                    }
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = find(rawToken, AccountTokenType.PASSWORD_RESET);
        Instant now = clock.instant();
        if (token.isConsumed() || token.isExpired(now)
                || token.getUser().getAuthProvider() != AuthProvider.LOCAL
                || token.getUser().getPasswordHash() == null
                || !token.getUser().isActive()) {
            throw invalidReset();
        }
        token.getUser().changePassword(passwordEncoder.encode(newPassword));
        token.consume(now);
    }

    @Transactional
    public void changePassword(Jwt jwt, String currentPassword, String newPassword) {
        User user = userService.findCurrentUser(jwt);
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new InvalidCurrentPasswordException(
                    "Alteração de senha disponível apenas para contas com e-mail e senha");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Senha atual incorreta");
        }

        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword));
        tokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                        user.getId(), AccountTokenType.PASSWORD_RESET)
                .forEach(token -> token.consume(now));
    }

    private String issue(User user, AccountTokenType type, Duration expiration) {
        Instant now = clock.instant();
        tokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(user.getId(), type)
                .forEach(token -> token.consume(now));
        String rawToken = generateToken();
        tokenRepository.save(new AccountToken(user, type, hash(rawToken), now.plus(expiration), now));
        return rawToken;
    }

    private AccountToken find(String rawToken, AccountTokenType type) {
        if (rawToken == null || rawToken.isBlank()) throw invalid(type);
        return tokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(() -> invalid(type));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private InvalidAccountTokenException invalid(AccountTokenType type) {
        return type == AccountTokenType.EMAIL_VERIFICATION ? invalidVerification() : invalidReset();
    }

    private InvalidAccountTokenException invalidVerification() {
        return new InvalidAccountTokenException("Token de verificação inválido ou expirado");
    }

    private InvalidAccountTokenException invalidReset() {
        return new InvalidAccountTokenException("Token de recuperação inválido ou expirado");
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

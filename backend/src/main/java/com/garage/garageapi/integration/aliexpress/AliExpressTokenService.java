package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class AliExpressTokenService {
    private final AliExpressAuthorizationRepository repository;
    private final AliExpressTokenCipher cipher;
    private final AliExpressAuthService auth;
    private final String authorizationId;
    public AliExpressTokenService(AliExpressAuthorizationRepository repository, AliExpressTokenCipher cipher,
            AliExpressAuthService auth, @Value("${aliexpress.app-key:}") String appKey) {
        this.repository = repository; this.cipher = cipher; this.auth = auth;
        authorizationId = AliExpressOAuthStateService.hash(appKey);
    }
    @Transactional
    public void store(AliExpressTokenData data) {
        AliExpressAuthorization row = repository.findLocked(authorizationId).orElseGet(() -> new AliExpressAuthorization(authorizationId));
        update(row, data, false);
        repository.saveAndFlush(row);
    }
    @Transactional
    public String accessToken() {
        AliExpressAuthorization row = repository.findLocked(authorizationId).orElseThrow(this::reauthorize);
        if (!row.accessExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            if (row.refreshCiphertext() == null || row.refreshExpiresAt() == null || !row.refreshExpiresAt().isAfter(Instant.now())) throw reauthorize();
            AliExpressTokenData renewed;
            try { renewed = auth.refreshToken(cipher.decrypt(row.refreshCiphertext(), authorizationId + ":refresh")); }
            catch (AliExpressAuthException ignored) { throw reauthorize(); }
            update(row, renewed, true);
            repository.saveAndFlush(row);
        }
        return cipher.decrypt(row.accessCiphertext(), authorizationId + ":access");
    }
    /** Diagnostic use only: no refresh, writes or pessimistic lock. Never return to an HTTP caller. */
    @Transactional(readOnly = true)
    public String existingAccessToken() {
        AliExpressAuthorization row = repository.findById(authorizationId).orElseThrow(this::reauthorize);
        if (row.accessExpiresAt() == null || !row.accessExpiresAt().isAfter(Instant.now().plusSeconds(30))) throw reauthorize();
        String token = cipher.decrypt(row.accessCiphertext(), authorizationId + ":access");
        if (token == null || token.isBlank()) throw reauthorize();
        return token;
    }
    private void update(AliExpressAuthorization row, AliExpressTokenData data, boolean refreshing) {
        Instant now = Instant.now();
        if (data == null || data.accessToken() == null || data.accessToken().length() > 2048
                || (data.refreshToken() != null && data.refreshToken().length() > 2048)) throw reauthorize();
        Instant accessExpiry = expiry(data.expireTime(), data.expiresIn(), now, "access_token_expiration");
        Instant refreshExpiry = data.refreshExpiresIn() != null && data.refreshExpiresIn() <= 0 ? null
                : expiry(data.refreshTokenValidTime(), data.refreshExpiresIn(), now, "refresh_token_expiration");
        if (data.accessToken() == null || data.accessToken().isBlank() || accessExpiry == null || !accessExpiry.isAfter(now)) throw reauthorize();
        if (refreshing && row.refreshExpiresAt() != null && refreshExpiry != null && row.refreshExpiresAt().isBefore(refreshExpiry)) {
            // Refresh rotates credentials but must not extend the original refresh authorization window.
            refreshExpiry = row.refreshExpiresAt();
        }
        row.update(cipher.encrypt(data.accessToken(), authorizationId + ":access"),
                data.refreshToken() == null || data.refreshToken().isBlank() ? null : cipher.encrypt(data.refreshToken(), authorizationId + ":refresh"),
                accessExpiry, refreshExpiry);
    }
    private static Instant expiry(Long absoluteMillis, Long seconds, Instant now, String field) {
        try { return absoluteMillis != null ? Instant.ofEpochMilli(absoluteMillis) : seconds == null ? null : now.plusSeconds(seconds); }
        catch (Exception ignored) {
            org.slf4j.LoggerFactory.getLogger(AliExpressTokenService.class).warn(
                    "AliExpress preview incomplete; stage=token_expiry; field={}; condition=invalid_expiration", field);
            throw new AliExpressIntegrationException(AliExpressIntegrationException.Reason.INCOMPLETE);
        }
    }
    private AliExpressIntegrationException reauthorize() { return new AliExpressIntegrationException(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED); }
}

package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Read-only local evidence. Never refreshes, exchanges or returns credentials. */
@Service
public class AliExpressAuthorizationDiagnostics {
    private final AliExpressAuthorizationRepository repository;
    private final AliExpressTokenCipher cipher;
    private final String authorizationId;
    private final boolean configured;

    public AliExpressAuthorizationDiagnostics(AliExpressAuthorizationRepository repository, AliExpressTokenCipher cipher,
            @Value("${aliexpress.app-key:}") String appKey,
            @Value("${aliexpress.app-secret:}") String appSecret) {
        this.repository = repository; this.cipher = cipher;
        authorizationId = AliExpressOAuthStateService.hash(appKey);
        configured = present(appKey) && present(appSecret);
    }

    public record Report(boolean applicationConfigured, String accessTokenState, Instant accessExpiresAt,
                         String providerValidation, String freightPermission, boolean refreshAttempted) {}

    @Transactional(readOnly = true)
    public Report inspect() {
        if (!configured) return report("APPLICATION_NOT_CONFIGURED", null);
        final AliExpressAuthorization row;
        try { row = repository.findById(authorizationId).orElse(null); }
        catch (RuntimeException ignored) { return report("STORAGE_UNAVAILABLE", null); }
        if (row == null) return report("AUTHORIZATION_NOT_FOUND", null);
        Instant expiry = row.accessExpiresAt();
        if (expiry == null || !expiry.isAfter(Instant.now().plusSeconds(30))) return report("EXPIRED_OR_EXPIRING", expiry);
        try {
            String token = cipher.decrypt(row.accessCiphertext(), authorizationId + ":access");
            return report(token == null || token.isBlank() ? "UNREADABLE" : "LOCALLY_USABLE", expiry);
        } catch (RuntimeException ignored) { return report("UNREADABLE", expiry); }
    }

    private Report report(String state, Instant expiry) {
        return new Report(configured, state, expiry, "NOT_VERIFIED", "NOT_VERIFIED", false);
    }
    private static boolean present(String value) { return value != null && !value.isBlank() && !value.contains("COLE_AQUI"); }
}

package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class AliExpressOAuthStateService {
    private final AliExpressOAuthStateRepository repository;
    private final AliExpressTokenCipher cipher;
    private final String appKey, callbackUrl;
    public AliExpressOAuthStateService(AliExpressOAuthStateRepository repository, AliExpressTokenCipher cipher,
            @Value("${aliexpress.app-key:}") String appKey, @Value("${aliexpress.callback-url:}") String callbackUrl) {
        this.repository = repository; this.cipher = cipher; this.appKey = appKey; this.callbackUrl = callbackUrl;
    }
    public record AuthorizationUrl(String authorizationUrl) {
        @Override public String toString() { return "AliExpressAuthorizationUrl[REDACTED]"; }
    }
    @Transactional
    public AuthorizationUrl start() {
        cipher.requireConfigured();
        URI callback;
        try { callback = URI.create(callbackUrl); } catch (Exception ignored) { throw invalid(); }
        if (appKey.isBlank() || appKey.contains("COLE_AQUI") || callback.getHost() == null
                || callback.getRawUserInfo() != null || callback.getRawFragment() != null
                || !("https".equals(callback.getScheme()) || ("http".equals(callback.getScheme()) && "localhost".equals(callback.getHost())))) throw invalid();
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new AliExpressOAuthState(hash(state), Instant.now().plusSeconds(600)));
        return new AuthorizationUrl("https://api-sg.aliexpress.com/oauth/authorize?response_type=code&force_auth=true&client_id="
                + encode(appKey) + "&redirect_uri=" + encode(callbackUrl) + "&state=" + encode(state));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String state) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        AliExpressOAuthState stored = repository.findLocked(hash(state)).orElseThrow(this::invalid);
        Instant now = Instant.now();
        if (!stored.usable(now)) throw invalid();
        stored.consume(now);
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 indisponivel"); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private AliExpressIntegrationException invalid() { return new AliExpressIntegrationException(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED); }
}

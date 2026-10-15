package com.garage.garageapi.integration.aliexpress;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "aliexpress_oauth_states")
public class AliExpressOAuthState {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    protected AliExpressOAuthState() {}
    AliExpressOAuthState(String hash, Instant expiry) { id = hash; expiresAt = expiry; }
    boolean usable(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
    void consume(Instant now) { consumedAt = now; }
}

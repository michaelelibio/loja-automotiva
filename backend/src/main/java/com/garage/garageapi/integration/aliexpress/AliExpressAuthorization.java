package com.garage.garageapi.integration.aliexpress;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "aliexpress_authorizations")
public class AliExpressAuthorization {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 4096) private String accessCiphertext;
    @Column(length = 4096) private String refreshCiphertext;
    @Column(nullable = false) private Instant accessExpiresAt;
    private Instant refreshExpiresAt;
    @Version private Long version;
    protected AliExpressAuthorization() {}
    AliExpressAuthorization(String id) { this.id = id; }
    void update(String access, String refresh, Instant accessExpiry, Instant refreshExpiry) {
        accessCiphertext = access; refreshCiphertext = refresh;
        accessExpiresAt = accessExpiry; refreshExpiresAt = refreshExpiry;
    }
    String accessCiphertext() { return accessCiphertext; }
    String refreshCiphertext() { return refreshCiphertext; }
    Instant accessExpiresAt() { return accessExpiresAt; }
    Instant refreshExpiresAt() { return refreshExpiresAt; }
}

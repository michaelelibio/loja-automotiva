package com.garage.garageapi.payment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Immutable financial observations from authenticated provider lookups; contains no recipient data. */
@Entity
@Table(name = "payment_financial_events")
public class PaymentFinancialEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Payment payment;
    @Column(name = "snapshot_key", nullable = false, unique = true, length = 64, updatable = false)
    private String snapshotKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30, updatable = false)
    private PaymentStatus status;
    @Column(name = "transaction_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal transactionAmount;
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal refundedAmount;
    @Column(name = "provider_updated_at", updatable = false) private Instant providerUpdatedAt;
    @Column(name = "observed_at", nullable = false, updatable = false) private Instant observedAt;
    protected PaymentFinancialEvent() { }
    public PaymentFinancialEvent(Payment payment, BigDecimal transactionAmount, Instant observedAt) {
        this.payment = payment; this.status = payment.getStatus(); this.transactionAmount = transactionAmount;
        refundedAmount = payment.getRefundedAmount(); providerUpdatedAt = payment.getProviderUpdatedAt(); this.observedAt = observedAt;
        try {
            String value = payment.getId()+"|"+providerUpdatedAt+"|"+status+"|"+refundedAmount.stripTrailingZeros().toPlainString();
            snapshotKey = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 indisponível", exception); }
    }
    public Long getId() { return id; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getProviderUpdatedAt() { return providerUpdatedAt; }
}

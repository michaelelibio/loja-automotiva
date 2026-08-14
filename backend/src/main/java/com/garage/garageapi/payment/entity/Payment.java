package com.garage.garageapi.payment.entity;

import com.garage.garageapi.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "provider_payment_id", length = 255)
    private String providerPaymentId;

    @Column(name = "provider_order_id", unique = true, length = 255)
    private String providerOrderId;

    @Column(name = "idempotency_key", unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "qr_code", length = 5000)
    private String qrCode;

    @Column(name = "qr_code_base64", columnDefinition = "text")
    private String qrCodeBase64;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Payment() { }

    public Payment(Order order, PaymentMethod method) {
        this.order = order;
        this.method = method;
        this.status = PaymentStatus.PENDING;
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getQrCode() { return qrCode; }
    public String getQrCodeBase64() { return qrCodeBase64; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void ensureIdempotencyKey() {
        if (idempotencyKey == null) idempotencyKey = UUID.randomUUID().toString();
    }

    public void markFailed() {
        status = PaymentStatus.FAILED;
    }

    public void applyProviderResult(String providerOrderId, String providerPaymentId,
                                    PaymentStatus status, String qrCode,
                                    String qrCodeBase64, Instant expiresAt, Instant paidAt) {
        this.providerOrderId = providerOrderId;
        this.providerPaymentId = providerPaymentId;
        this.status = status;
        this.qrCode = qrCode;
        this.qrCodeBase64 = qrCodeBase64;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
    }

    public void applyProviderResult(String providerPaymentId, PaymentStatus status, String qrCode,
                                    String qrCodeBase64, Instant expiresAt, Instant paidAt) {
        applyProviderResult(providerOrderId, providerPaymentId, status, qrCode, qrCodeBase64,
                expiresAt, paidAt);
    }

    public boolean synchronizeProviderStatus(PaymentStatus providerStatus, Instant providerPaidAt) {
        if (status == PaymentStatus.PAID) {
            if (paidAt == null && providerPaidAt != null) {
                paidAt = providerPaidAt;
                return true;
            }
            return false;
        }
        if (status == providerStatus) return false;
        if (providerStatus == PaymentStatus.PAID) {
            status = PaymentStatus.PAID;
            paidAt = providerPaidAt;
            return true;
        }
        if (status != PaymentStatus.PENDING) return false;
        status = providerStatus;
        return true;
    }
}

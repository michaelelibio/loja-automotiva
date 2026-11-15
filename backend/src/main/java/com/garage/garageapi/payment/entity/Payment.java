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
import java.math.BigDecimal;
import java.util.UUID;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;

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

    @Column(name = "provider_preference_id", unique = true, length = 255)
    private String providerPreferenceId;

    @Column(name = "external_reference", unique = true, length = 255)
    private String externalReference;

    @Column(name = "checkout_url", length = 2000)
    private String checkoutUrl;

    @Column(name = "provider_payment_type", length = 100)
    private String providerPaymentType;

    @Column(name = "provider_payment_method_id", length = 100)
    private String providerPaymentMethodId;

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

    @Column(name = "provider_live_mode")
    private Boolean providerLiveMode;
    @Column(name = "provider_verified_at")
    private Instant providerVerifiedAt;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;
    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(name = "provider_status_detail", length = 120)
    private String providerStatusDetail;

    public Instant getProviderUpdatedAt() { return providerUpdatedAt; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public String getProviderStatusDetail() { return providerStatusDetail; }

    public enum SnapshotDisposition { APPLIED, DUPLICATE, STALE, INVALID }

    /** Apply only an authenticated, associated provider lookup while holding the payment lock. */
    public SnapshotDisposition synchronizeProviderSnapshot(CheckoutProGateway.PaymentResult result, Instant checkedAt) {
        BigDecimal amount = result.transactionAmount();
        BigDecimal refund = result.refundedAmount();
        if (result.status() == null || amount == null || amount.signum() <= 0 || refund == null
                || refund.signum() < 0 || refund.compareTo(amount) > 0 || refund.scale() > 2
                || result.status() == PaymentStatus.PAID && result.approvedAt() == null
                || result.status() == PaymentStatus.PARTIALLY_REFUNDED && (refund.signum() == 0 || refund.compareTo(amount) >= 0)
                || result.status() == PaymentStatus.REFUNDED && refund.compareTo(amount) != 0)
            return SnapshotDisposition.INVALID;
        Instant incoming = result.providerUpdatedAt();
        if (incoming != null && incoming.isAfter(checkedAt.plusSeconds(300))) return SnapshotDisposition.INVALID;
        if (providerUpdatedAt != null && (incoming == null || incoming.isBefore(providerUpdatedAt)))
            return SnapshotDisposition.STALE;
        BigDecimal previousRefund = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
        // A completed refund cannot disappear because a later provider response omitted its amount.
        BigDecimal effectiveRefund = refund.max(previousRefund);
        PaymentStatus next = result.status();
        if (next != PaymentStatus.CHARGED_BACK && next != PaymentStatus.DISPUTED && effectiveRefund.signum() > 0)
            next = effectiveRefund.compareTo(amount) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        if (providerUpdatedAt != null && providerUpdatedAt.equals(incoming)) {
            if (status != next || previousRefund.compareTo(effectiveRefund) != 0)
                return SnapshotDisposition.STALE;
            recordProviderEnvironment(result.liveMode(), checkedAt);
            return SnapshotDisposition.DUPLICATE;
        }
        // Legacy/unversioned fixtures may establish an initial state, but cannot undo a reversal/dispute.
        if (incoming == null && status.requiresFinancialReview() && next == PaymentStatus.PAID)
            return SnapshotDisposition.STALE;
        if (incoming == null && status == PaymentStatus.PAID
                && (next == PaymentStatus.PENDING || next == PaymentStatus.FAILED || next == PaymentStatus.EXPIRED))
            return SnapshotDisposition.STALE;
        boolean changed = status != next || previousRefund.compareTo(effectiveRefund) != 0
                || providerPaymentId == null || providerUpdatedAt == null && incoming != null;
        if (providerPaymentId == null) providerPaymentId = result.providerPaymentId();
        status = next;
        refundedAmount = effectiveRefund;
        providerUpdatedAt = incoming;
        providerPaymentType = result.paymentType();
        providerPaymentMethodId = result.paymentMethodId();
        providerStatusDetail = result.statusDetail() != null && result.statusDetail().matches("[A-Za-z0-9_.:-]{1,120}")
                ? result.statusDetail() : null;
        if (result.approvedAt() != null) paidAt = result.approvedAt();
        recordProviderEnvironment(result.liveMode(), checkedAt);
        return changed ? SnapshotDisposition.APPLIED : SnapshotDisposition.DUPLICATE;
    }

    public Boolean getProviderLiveMode() { return providerLiveMode; }
    public Instant getProviderVerifiedAt() { return providerVerifiedAt; }
    /** Called only after validating the authenticated provider lookup and its order association. */
    public void recordProviderEnvironment(Boolean liveMode, Instant checkedAt) {
        providerLiveMode = liveMode;
        providerVerifiedAt = checkedAt;
    }

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
    public String getProviderPreferenceId() { return providerPreferenceId; }
    public String getExternalReference() { return externalReference; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getProviderPaymentType() { return providerPaymentType; }
    public String getProviderPaymentMethodId() { return providerPaymentMethodId; }
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

    /**
     * Legacy hydration hook retained for historical PIX fixtures and migrations only.
     * New payment flows must use Checkout Pro fields.
     */
    @Deprecated(forRemoval = false)
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

    public void applyCheckoutPreference(String preferenceId, String externalReference,
                                        String checkoutUrl) {
        this.providerPreferenceId = preferenceId;
        this.externalReference = externalReference;
        this.checkoutUrl = checkoutUrl;
    }

    /** Legacy hydration only. Production provider responses use synchronizeProviderSnapshot. */
    @Deprecated(forRemoval = false)
    public void synchronizeCheckoutPayment(String providerPaymentId, PaymentStatus providerStatus,
                                           Instant providerPaidAt, String paymentType,
                                           String paymentMethodId) {
        if (this.providerPaymentId == null) this.providerPaymentId = providerPaymentId;
        this.providerPaymentType = paymentType;
        this.providerPaymentMethodId = paymentMethodId;
        synchronizeProviderStatus(providerStatus, providerPaidAt);
    }

    public boolean synchronizeProviderStatus(PaymentStatus providerStatus, Instant providerPaidAt) {
        if (providerStatus.requiresFinancialReview() && status != providerStatus) {
            status = providerStatus;
            return true;
        }
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

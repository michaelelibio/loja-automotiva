package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.order.entity.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_fulfillments", uniqueConstraints = @UniqueConstraint(
        name = "uk_order_fulfillments_order", columnNames = "order_id"))
public class OrderFulfillment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FulfillmentStatus status;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "supplier_order_id", unique = true, length = 200)
    private String supplierOrderId;

    @Column(name = "supplier_shipment_order_id", length = 200)
    private String supplierShipmentOrderId;

    @Column(name = "external_reference", nullable = false, unique = true, length = 50)
    private String externalReference;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "created_externally_at")
    private Instant createdExternallyAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrderFulfillment() { }

    public OrderFulfillment(Order order, boolean required) {
        this.order = order;
        this.status = required ? FulfillmentStatus.PENDING : FulfillmentStatus.NOT_REQUIRED;
        this.provider = required ? "CJ" : "NONE";
        this.externalReference = "INGARAGE-" + order.getId();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String claim(Instant now) {
        if (status != FulfillmentStatus.PENDING && status != FulfillmentStatus.FAILED) return null;
        status = FulfillmentStatus.PROCESSING;
        processingToken = UUID.randomUUID().toString();
        processingStartedAt = now;
        attemptCount++;
        lastError = null;
        updatedAt = now;
        return processingToken;
    }

    public boolean complete(String token, String orderId, String shipmentOrderId, Instant now) {
        if (status != FulfillmentStatus.PROCESSING || !processingToken.equals(token)) return false;
        status = FulfillmentStatus.CREATED;
        supplierOrderId = orderId;
        supplierShipmentOrderId = shipmentOrderId;
        processingToken = null;
        createdExternallyAt = now;
        updatedAt = now;
        return true;
    }

    public boolean fail(String token, String error, Instant now) {
        if (status != FulfillmentStatus.PROCESSING || !processingToken.equals(token)) return false;
        status = FulfillmentStatus.FAILED;
        lastError = error;
        processingToken = null;
        updatedAt = now;
        return true;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public FulfillmentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getSupplierOrderId() { return supplierOrderId; }
    public String getSupplierShipmentOrderId() { return supplierShipmentOrderId; }
    public String getExternalReference() { return externalReference; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public Instant getCreatedExternallyAt() { return createdExternallyAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

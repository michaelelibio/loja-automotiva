package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.order.entity.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Locale;
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

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "supplier_order_ids", columnDefinition = "jsonb")
    private java.util.List<String> supplierOrderIds;
    @Column(name = "supplier_status", length = 40)
    private String supplierStatus;

    public java.util.List<String> getSupplierOrderIds() { return supplierOrderIds == null ? java.util.List.of() : java.util.List.copyOf(supplierOrderIds); }
    public String getSupplierStatus() { return supplierStatus; }

    public void prepareAliExpress() {
        if (status == FulfillmentStatus.NOT_REQUIRED && "NONE".equals(provider) && attemptCount == 0 && supplierOrderId == null) {
            provider = "ALIEXPRESS"; status = FulfillmentStatus.PENDING;
        }
    }

    @ElementCollection
    @CollectionTable(name="manual_supplier_order_refs", joinColumns=@JoinColumn(name="fulfillment_id"))
    @Column(name="supplier_order_id", nullable=false, unique=true, length=30)
    private java.util.Set<String> manualSupplierRefs = new java.util.HashSet<>();

    public void beginManualAliExpress() {
        prepareAliExpress();
        String token = beginAliExpress(Instant.now());
        if (token == null) throw new com.garage.garageapi.shared.exception.ResourceConflictException("Encaminhamento já iniciado. Confira no fornecedor; não crie outro pedido.");
        supplierStatus = "MANUAL_IN_PROGRESS";
    }
    public void finishManualAliExpress(java.util.List<String> ids) {
        if (status == FulfillmentStatus.CREATED && getSupplierOrderIds().equals(ids)) return;
        if (status != FulfillmentStatus.VERIFICATION_REQUIRED || !"MANUAL_IN_PROGRESS".equals(supplierStatus)
                || ids == null || ids.isEmpty() || new java.util.HashSet<>(ids).size() != ids.size())
            throw new com.garage.garageapi.shared.exception.ResourceConflictException("Inicie o encaminhamento manual uma única vez antes de registrar os IDs.");
        manualSupplierRefs.addAll(ids);
        finishAliExpress(processingToken,ids,false,Instant.now());
    }

    public String beginAliExpress(Instant now) {
        if (!"ALIEXPRESS".equals(provider)) return null;
        String token = claim(now);
        if (token == null) return null;
        // Commit uncertainty BEFORE the network call. Crashes and concurrent requests must never resend.
        markCreationAttempt(token,now);
        status = FulfillmentStatus.VERIFICATION_REQUIRED;
        supplierStatus = "UNKNOWN";
        lastError = "Verificação necessária. Não reenviar antes da reconciliação.";
        return token;
    }

    public void finishAliExpress(String token, java.util.List<String> ids, boolean rejected, Instant now) {
        if (!"ALIEXPRESS".equals(provider) || status != FulfillmentStatus.VERIFICATION_REQUIRED
                || token == null || !token.equals(processingToken)) return;
        if (ids != null && !ids.isEmpty()) {
            supplierOrderIds = java.util.List.copyOf(ids);
            supplierOrderId = ids.get(0);
            status = FulfillmentStatus.CREATED;
            supplierStatus = "AWAITING_MANUAL_PAYMENT";
            lastError = null;
            createdExternallyAt = now;
            if (order.getStatus() == com.garage.garageapi.order.entity.OrderStatus.PAID) order.startProcessing(now);
        } else if (rejected) {
            status = FulfillmentStatus.FAILED;
            supplierStatus = "REJECTED";
            lastError = "O AliExpress recusou a criação. Confira os dados e o frete antes de tentar novamente.";
        }
        processingToken = null;
        updatedAt = now;
    }

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

    public OrderFulfillment(Order order, String provider) {
        this.order = order;
        boolean required = provider != null && !provider.isBlank() && !"NONE".equals(provider);
        this.status = required ? FulfillmentStatus.PENDING : FulfillmentStatus.NOT_REQUIRED;
        this.provider = required ? provider.toUpperCase(Locale.ROOT) : "NONE";
        this.externalReference = "INGARAGE-" + order.getId();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String claim(Instant now) {
        if (status != FulfillmentStatus.PENDING && status != FulfillmentStatus.FAILED) return null;
        status = FulfillmentStatus.PROCESSING;
        processingToken = UUID.randomUUID().toString();
        processingStartedAt = now;
        lastError = null;
        updatedAt = now;
        return processingToken;
    }

    public boolean markCreationAttempt(String token, Instant now) {
        if (status != FulfillmentStatus.PROCESSING || !processingToken.equals(token)) return false;
        attemptCount++;
        updatedAt = now;
        return true;
    }

    public boolean release(String token, FulfillmentStatus target, Instant now) {
        if (status != FulfillmentStatus.PROCESSING || !processingToken.equals(token)
                || (target != FulfillmentStatus.PENDING && target != FulfillmentStatus.FAILED)) return false;
        status = target;
        processingToken = null;
        updatedAt = now;
        return true;
    }

    public boolean complete(String token, String orderId, String shipmentOrderId, Instant now) {
        if (status != FulfillmentStatus.PROCESSING || !processingToken.equals(token)) return false;
        status = FulfillmentStatus.CREATED;
        supplierOrderId = orderId;
        if (shipmentOrderId != null && !shipmentOrderId.isBlank()) {
            supplierShipmentOrderId = shipmentOrderId;
        }
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

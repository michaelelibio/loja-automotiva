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

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "supplier_sync", columnDefinition = "jsonb")
    private com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot supplierSync;
    public com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot getSupplierSync() { return supplierSync; }
    public String beginSupplierSync(Instant now) {
        if (!"ALIEXPRESS".equals(provider) || status!=FulfillmentStatus.CREATED || getSupplierOrderIds().isEmpty())
            throw new com.garage.garageapi.shared.exception.ResourceConflictException("Não há pedido AliExpress confirmado para consultar.");
        if (supplierSync!=null && supplierSync.token()!=null && supplierSync.startedAt().isAfter(now.minusSeconds(120)))
            throw new com.garage.garageapi.shared.exception.ResourceConflictException("Consulta do fornecedor em andamento.");
        String token=UUID.randomUUID().toString();
        supplierSync=new com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot(token,now,
                supplierSync==null ? null : supplierSync.checkedAt(),supplierSync==null ? java.util.List.of() : supplierSync.parts());
        return token;
    }
    public void finishSupplierSync(String token, java.util.List<String> ids,
            java.util.List<com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot.Part> parts, Instant now) {
        if (supplierSync==null || !java.util.Objects.equals(token,supplierSync.token()) || !getSupplierOrderIds().equals(ids)) return;
        if (!parts.stream().map(com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot.Part::orderId).toList().equals(ids))
            throw new IllegalArgumentException("Resultado não corresponde aos pedidos consultados.");
        supplierSync=new com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot(null,supplierSync.startedAt(),now,java.util.List.copyOf(parts));
        var states=parts.stream().map(com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot.Part::state).distinct().toList();
        supplierStatus=states.size()==1 ? states.get(0) : "MIXED";
        if(!parts.isEmpty() && parts.stream().allMatch(p->p.costs()!=null && p.costs().total()!=null)
                && parts.stream().map(p->p.costs().currency()).distinct().count()==1) {
            var total=parts.stream().map(p->p.costs().total()).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
            String currency=parts.get(0).costs().currency();
            var product=supplierCosts!=null && currency.equals(supplierCosts.currency())?supplierCosts.productTotal():null;
            supplierCosts=new com.garage.garageapi.integration.aliexpress.AliExpressSupplierCosts("SUPPLIER_ORDER_API",currency,product,null,null,total,now);
        }
        updatedAt=now;
    }

    public java.util.List<String> getSupplierOrderIds() { return supplierOrderIds == null ? java.util.List.of() : java.util.List.copyOf(supplierOrderIds); }
    public String getSupplierStatus() { return supplierStatus; }

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "dispatch_quote", columnDefinition = "jsonb")
    private com.garage.garageapi.integration.aliexpress.AliExpressDispatchQuote dispatchQuote;
    @Column(name = "dispatch_check_count", nullable = false)
    private int dispatchCheckCount;
    @Column(name = "dispatch_next_check_at")
    private Instant dispatchNextCheckAt;
    @Column(name = "dispatch_payload_hash", length = 64)
    private String dispatchPayloadHash;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name="supplier_costs",columnDefinition="jsonb")
    private com.garage.garageapi.integration.aliexpress.AliExpressSupplierCosts supplierCosts;
    @Column(name="dispatch_manual_hold",nullable=false) private boolean dispatchManualHold;
    @Column(name="dispatch_review_context",length=64) private String dispatchReviewContext;
    @Column(name="dispatch_review_at") private Instant dispatchReviewAt;
    public boolean isDispatchManualHold() {return dispatchManualHold;}
    public boolean manualRetryReady(String context) {return dispatchManualHold && status==FulfillmentStatus.FAILED && java.util.Objects.equals(context,dispatchReviewContext)
            && dispatchReviewAt!=null && Instant.now().isBefore(dispatchReviewAt.plusSeconds(900));}
    public boolean recipientEditSafe(Instant now) {
        return "ALIEXPRESS".equals(provider) && status!=FulfillmentStatus.CREATED && status!=FulfillmentStatus.PROCESSING
                && supplierOrderId==null && getSupplierOrderIds().isEmpty()
                && (processingToken==null || processingStartedAt==null || !now.isBefore(processingStartedAt.plusSeconds(120)));
    }
    public void holdForRecipientCorrection() {
        dispatchManualHold=true;dispatchReviewContext=null;dispatchReviewAt=null;dispatchQuote=null;dispatchNextCheckAt=null;
    }
    public void approveManualRetry(String context,Instant now) {
        if(!dispatchManualHold || !recipientEditSafe(now) || attemptCount>=3
                || processingStartedAt!=null && now.isBefore(processingStartedAt.plusSeconds(120)))
            throw new com.garage.garageapi.shared.exception.ResourceConflictException("Reconciliação não pode liberar este pedido. Confira a tentativa anterior e os IDs existentes.");
        dispatchReviewContext=context;dispatchReviewAt=now;status=FulfillmentStatus.FAILED;processingToken=null;supplierStatus="MANUAL_RETRY_READY";updatedAt=now;
    }
    public com.garage.garageapi.integration.aliexpress.AliExpressSupplierCosts getSupplierCosts() { return supplierCosts; }
    public void recordSupplierCosts(com.garage.garageapi.integration.aliexpress.AliExpressSupplierCosts costs) { supplierCosts=costs; }

    /** Explicit state only; the transient restored FSM is never persisted through JPA. */
    public record AliExpressState(int version,FulfillmentStatus status,String supplierOrderId,java.util.List<String> supplierOrderIds,
            String supplierStatus,String processingToken,int attemptCount,String lastError,Instant processingStartedAt,
            Instant createdExternallyAt,Instant createdAt,Instant updatedAt,
            com.garage.garageapi.integration.aliexpress.AliExpressSupplierSnapshot supplierSync,
            com.garage.garageapi.integration.aliexpress.AliExpressDispatchQuote dispatchQuote,int dispatchCheckCount,
            Instant dispatchNextCheckAt,String dispatchPayloadHash,
            com.garage.garageapi.integration.aliexpress.AliExpressSupplierCosts supplierCosts,boolean dispatchManualHold,String dispatchReviewContext,Instant dispatchReviewAt) { }
    public AliExpressState aliExpressState() {
        return new AliExpressState(1,status,supplierOrderId,getSupplierOrderIds(),supplierStatus,processingToken,attemptCount,lastError,
                processingStartedAt,createdExternallyAt,createdAt,updatedAt,supplierSync,dispatchQuote,dispatchCheckCount,dispatchNextCheckAt,dispatchPayloadHash,supplierCosts,dispatchManualHold,dispatchReviewContext,dispatchReviewAt);
    }
    public static OrderFulfillment restoreAliExpress(Order order,AliExpressState state) {
        var f=new OrderFulfillment(order,"ALIEXPRESS");
        f.externalReference="INGARAGE-ALI-"+order.getId();
        if(state==null)return f;
        if(state.version()!=1)throw new IllegalStateException("Unsupported AliExpress state version");
        f.status=state.status();f.supplierOrderId=state.supplierOrderId();f.supplierOrderIds=state.supplierOrderIds();f.supplierStatus=state.supplierStatus();
        f.processingToken=state.processingToken();f.attemptCount=state.attemptCount();f.lastError=state.lastError();f.processingStartedAt=state.processingStartedAt();
        f.createdExternallyAt=state.createdExternallyAt();f.createdAt=state.createdAt();f.updatedAt=state.updatedAt();f.supplierSync=state.supplierSync();
        f.dispatchQuote=state.dispatchQuote();f.dispatchCheckCount=state.dispatchCheckCount();f.dispatchNextCheckAt=state.dispatchNextCheckAt();
        f.dispatchPayloadHash=state.dispatchPayloadHash();f.supplierCosts=state.supplierCosts();f.dispatchManualHold=state.dispatchManualHold();f.dispatchReviewContext=state.dispatchReviewContext();f.dispatchReviewAt=state.dispatchReviewAt();return f;
    }

    public com.garage.garageapi.integration.aliexpress.AliExpressDispatchQuote getDispatchQuote() { return dispatchQuote; }
    public int getDispatchCheckCount() { return dispatchCheckCount; }
    public Instant getDispatchNextCheckAt() { return dispatchNextCheckAt; }
    public void approveDispatch(com.garage.garageapi.integration.aliexpress.AliExpressDispatchQuote quote) {
        if (!"ALIEXPRESS".equals(provider) || (status != FulfillmentStatus.PENDING && status != FulfillmentStatus.FAILED))
            throw new com.garage.garageapi.shared.exception.ResourceConflictException("Tentativa já iniciada. Reconcilie o fornecedor antes de alterar a cotação.");
        dispatchQuote = quote;
        dispatchCheckCount = 0;
        dispatchNextCheckAt = null;
        lastError = null;
        supplierStatus = "READY";
        updatedAt = Instant.now();
    }
    public void recordDispatchBlock(String message, boolean temporary) {
        if (!"ALIEXPRESS".equals(provider) || status != FulfillmentStatus.PENDING || attemptCount != 0) return;
        dispatchCheckCount++;
        lastError = message;
        supplierStatus = "BLOCKED";
        updatedAt = Instant.now();
        dispatchNextCheckAt = temporary && dispatchCheckCount < 3 ? updatedAt.plusSeconds(60L * dispatchCheckCount) : null;
    }
    public boolean automaticDispatchDue(Instant now) {
        return "ALIEXPRESS".equals(provider) && !dispatchManualHold && status == FulfillmentStatus.PENDING && attemptCount == 0
                && supplierOrderId == null && getSupplierOrderIds().isEmpty()
                && (lastError == null || dispatchNextCheckAt != null && !dispatchNextCheckAt.isAfter(now));
    }
    public void recordDispatchPayloadHash(String hash) { dispatchPayloadHash = hash; }
    public void recordUnconfirmedAliExpressResult(java.util.List<String> ids,
            com.garage.garageapi.integration.aliexpress.AliExpressOrderClient.ProviderError error) {
        if (status!=FulfillmentStatus.VERIFICATION_REQUIRED && status!=FulfillmentStatus.FAILED) return;
        if(ids!=null && !ids.isEmpty()) { supplierOrderIds=java.util.List.copyOf(ids);supplierOrderId=ids.get(0);manualSupplierRefs.addAll(ids);status=FulfillmentStatus.VERIFICATION_REQUIRED; }
        if(error!=null)lastError="AliExpress: "+error.code()+". "+(status==FulfillmentStatus.FAILED?"Criação recusada; confira os dados antes de nova tentativa.":"Resultado incerto. Reconcilie antes de reenviar.");
    }

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
        dispatchReviewContext=null;
        dispatchReviewAt=null;
        status = FulfillmentStatus.VERIFICATION_REQUIRED;
        supplierStatus = "UNKNOWN";
        lastError = "Verificação necessária. Não reenviar antes da reconciliação.";
        return token;
    }

    public void finishAliExpress(String token, java.util.List<String> ids, boolean rejected, Instant now) {
        if (!"ALIEXPRESS".equals(provider) || status != FulfillmentStatus.VERIFICATION_REQUIRED
                || token == null || !token.equals(processingToken)) return;
        if (ids != null && !ids.isEmpty()) {
            manualSupplierRefs.addAll(ids);
            supplierOrderIds = java.util.List.copyOf(ids);
            supplierOrderId = ids.get(0);
            status = FulfillmentStatus.CREATED;
            supplierStatus = "CREATED_UNPAID_UNVERIFIED";
            lastError = null;
            createdExternallyAt = now;
            if (order.getStatus() == com.garage.garageapi.order.entity.OrderStatus.PAID) order.startProcessingAfterSupplierCreation(now);
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

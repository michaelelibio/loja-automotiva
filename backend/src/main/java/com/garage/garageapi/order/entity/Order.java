package com.garage.garageapi.order.entity;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "shipping_code", nullable = false, length = 50)
    @ColumnDefault("'LEGACY'")
    private String shippingCode;

    @Column(name = "shipping_name", nullable = false, length = 120)
    @ColumnDefault("'Frete legado'")
    private String shippingName;

    @Column(name = "shipping_estimated_days", nullable = false)
    @ColumnDefault("0")
    private Integer shippingEstimatedDays;

    @Column(name = "shipping_provider", length = 30)
    private String shippingProvider;

    @Column(name = "shipping_provider_amount", precision = 19, scale = 4)
    private BigDecimal shippingProviderAmount;

    @Column(name = "shipping_provider_currency", length = 3)
    private String shippingProviderCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_legs", columnDefinition = "jsonb")
    private List<ShippingProvider.Leg> shippingLegs;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(length = 150)
    private String complement;

    @Column(nullable = false, length = 120)
    private String neighborhood;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    // Nullable recipient snapshots. Never exposed as personal values in order DTOs.
    @Column(name = "recipient_cpf", length = 11)
    private String recipientCpf;
    @Column(name = "recipient_birth_date")
    private java.time.LocalDate recipientBirthDate;
    @Column(name = "recipient_phone", length = 15)
    private String recipientPhone;

    @com.fasterxml.jackson.annotation.JsonIgnore public String getRecipientCpf() { return recipientCpf; }
    @com.fasterxml.jackson.annotation.JsonIgnore public java.time.LocalDate getRecipientBirthDate() { return recipientBirthDate; }
    @com.fasterxml.jackson.annotation.JsonIgnore public String getRecipientPhone() { return recipientPhone; }
    public void snapshotRecipient(com.garage.garageapi.order.recipient.AliExpressRecipientData.Normalized data) {
        recipientCpf=data.cpf(); recipientBirthDate=data.birthDate(); recipientPhone=data.phone();
    }

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assisted_quote", columnDefinition = "jsonb")
    private com.garage.garageapi.order.quote.AssistedQuote assistedQuote;

    @Column(name = "request_key", length = 36, unique = true)
    private String requestKey;

    public com.garage.garageapi.order.quote.AssistedQuote getAssistedQuote() { return assistedQuote; }
    public void requestAssistedQuote(String key) {
        if (assistedQuote != null) throw new ResourceConflictException("Cotação já solicitada.");
        requestKey = key;
        assistedQuote = com.garage.garageapi.order.quote.AssistedQuote.waiting();
    }
    public void confirmAssistedQuote(com.garage.garageapi.order.quote.AssistedQuote quote,
                                     List<ShippingProvider.Leg> legs) {
        if (status != OrderStatus.PENDING_PAYMENT || assistedQuote == null
                || !"WAITING".equals(assistedQuote.state()))
            throw new ResourceConflictException("Este pedido não pode receber outra cotação. Solicite um novo pedido.");
        assistedQuote = quote;
        shippingLegs = List.copyOf(legs);
        shippingCost = legs.stream().map(ShippingProvider.Leg::priceBrl).reduce(BigDecimal.ZERO, BigDecimal::add);
        shippingCode = "ASSISTED_QUOTE";
        shippingName = "Entrega AliExpress confirmada";
        shippingProvider = "ALIEXPRESS";
        shippingProviderCurrency = "BRL";
        shippingProviderAmount = shippingCost;
        shippingEstimatedDays = legs.stream().mapToInt(ShippingProvider.Leg::estimatedDays).max().orElseThrow();
        total = subtotal.add(shippingCost);
        expiresAt = quote.validUntil();
        assistedQuote = quote.withContextDigest(quoteContextDigest());
    }
    public void updateAssistedQuote(com.garage.garageapi.order.quote.AssistedQuote quote) { assistedQuote = quote; }
    /** Canonical digest survives JSONB/NUMERIC round trips and binds the confirmed commercial snapshot. */
    private String quoteContextDigest() {
        var lines=items.stream().sorted(java.util.Comparator.comparing(OrderItem::getProductId)
                .thenComparing(item -> java.util.Objects.toString(item.getProductVariantId(), "")))
                .map(item -> java.util.Arrays.asList(item.getProductId(),item.getProductVariantId(),
                    item.getSupplierProductId(),item.getSupplierVariantId(),item.getQuantity(),
                    decimal(item.getUnitPrice()),decimal(item.getSubtotal()))).toList();
        var legs=getShippingLegs().stream().sorted(java.util.Comparator.comparing(ShippingProvider.Leg::code)
                .thenComparing(leg -> String.join(",",leg.supplierVariantIds().stream().sorted().toList())))
                .map(leg -> java.util.Arrays.asList(leg.provider(),leg.code(),leg.name(),leg.originCountry(),
                    decimal(leg.amount()),leg.currency(),decimal(leg.priceBrl()),leg.estimatedDays(),
                    leg.supplierVariantIds().stream().sorted().toList())).toList();
        try {
            byte[] snapshot=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(java.util.Arrays.asList(
                "v1",id,recipientName,zipCode,street,number,complement,neighborhood,city,state,
                recipientCpf,recipientBirthDate==null ? null : recipientBirthDate.toString(),recipientPhone,
                lines,legs,decimal(subtotal),decimal(shippingCost),decimal(total)));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot));
        } catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException("SHA-256 indisponível",ex);}
    }
    private static String decimal(BigDecimal value) {return value==null ? null : value.stripTrailingZeros().toPlainString();}
    public void requireQuoteContextUnchanged() {
        if (assistedQuote!=null && (assistedQuote.contextDigest()==null
                || !assistedQuote.contextDigest().equals(quoteContextDigest())))
            throw new ResourceConflictException("Endereço, itens, modalidade ou preço divergiram da cotação. Solicite uma nova cotação.");
    }
    public void requireAssistedDispatch() {
        requireQuoteContextUnchanged();
        if (assistedQuote != null && (!"ACCEPTED".equals(assistedQuote.state())
                || assistedQuote.dispatchCheckedAt() == null
                || assistedQuote.dispatchCheckedAt().isBefore(Instant.now().minusSeconds(600))))
            throw new ResourceConflictException("Reconfira o custo no AliExpress antes do encaminhamento. Divergências exigem cancelamento/reembolso.");
    }
    public void holdAssistedPaymentForRefund() {
        if (assistedQuote==null || "REFUNDED".equals(assistedQuote.state())) return;
        assistedQuote=assistedQuote.checked(false);
        if (status==OrderStatus.EXPIRED || status==OrderStatus.CANCELED) status=OrderStatus.PAID;
    }
    public void cancelAfterRefund() {
        if (assistedQuote == null || !"REFUNDED".equals(assistedQuote.state())
                || (status != OrderStatus.PAID && status != OrderStatus.PROCESSING))
            throw new ResourceConflictException("Reembolso confirmado é obrigatório.");
        status = OrderStatus.CANCELED;
    }

    protected Order() { }

    public Order(User user, Address address, BigDecimal subtotal, BigDecimal shippingCost,
                 Duration expiration) {
        this(user, address, subtotal,
                new ShippingProvider.Option("LEGACY", "Frete legado", shippingCost, 0), expiration);
    }

    public Order(User user, Address address, BigDecimal subtotal, ShippingProvider.Option shipping,
                 Duration expiration) {
        this.user = user;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.subtotal = subtotal;
        this.shippingCode = shipping.code();
        this.shippingName = shipping.name();
        this.shippingCost = shipping.price();
        this.shippingEstimatedDays = shipping.estimatedDays();
        this.shippingProvider = shipping.provider();
        this.shippingProviderAmount = shipping.providerAmount();
        this.shippingProviderCurrency = shipping.providerCurrency();
        this.shippingLegs = List.copyOf(shipping.legs());
        this.total = subtotal.add(shipping.price());
        this.recipientName = address.getRecipientName();
        this.zipCode = address.getZipCode();
        this.street = address.getStreet();
        this.number = address.getNumber();
        this.complement = address.getComplement();
        this.neighborhood = address.getNeighborhood();
        this.city = address.getCity();
        this.state = address.getState();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now.plus(expiration);
    }

    public void addItem(OrderItem item) { items.add(item); }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShippingCost() { return shippingCost; }
    public String getShippingCode() { return shippingCode; }
    public String getShippingName() { return shippingName; }
    public Integer getShippingEstimatedDays() { return shippingEstimatedDays; }
    public String getShippingProvider() { return shippingProvider; }
    public BigDecimal getShippingProviderAmount() { return shippingProviderAmount; }
    public String getShippingProviderCurrency() { return shippingProviderCurrency; }
    public List<ShippingProvider.Leg> getShippingLegs() {
        return shippingLegs == null ? List.of() : List.copyOf(shippingLegs);
    }
    public BigDecimal getTotal() { return total; }
    public String getRecipientName() { return recipientName; }
    public String getZipCode() { return zipCode; }
    public String getStreet() { return street; }
    public String getNumber() { return number; }
    public String getComplement() { return complement; }
    public String getNeighborhood() { return neighborhood; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getProcessingAt() { return processingAt; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }

    public void markPaid() {
        requireStatus(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        status = OrderStatus.PAID;
    }

    public void startProcessing(Instant occurredAt) {
        requireAssistedDispatch();
        requireStatus(OrderStatus.PAID, OrderStatus.PROCESSING);
        status = OrderStatus.PROCESSING;
        processingAt = occurredAt;
    }

    public void markShipped(Instant occurredAt) {
        if (assistedQuote!=null && !"ACCEPTED".equals(assistedQuote.state()))
            throw new ResourceConflictException("Envio bloqueado: resolva o cancelamento/reembolso.");
        requireStatus(OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        status = OrderStatus.SHIPPED;
        shippedAt = occurredAt;
    }

    public void markDelivered(Instant occurredAt) {
        requireStatus(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        status = OrderStatus.DELIVERED;
        deliveredAt = occurredAt;
    }

    public void cancel() {
        requireStatus(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED);
        status = OrderStatus.CANCELED;
    }

    public void expire() {
        requireStatus(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
        status = OrderStatus.EXPIRED;
    }

    private void requireStatus(OrderStatus required, OrderStatus target) {
        if (status != required) {
            throw new ResourceConflictException(
                    "TransiÃ§Ã£o de pedido invÃ¡lida: " + status + " -> " + target);
        }
    }
}

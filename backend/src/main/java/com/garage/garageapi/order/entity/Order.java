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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

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
        requireStatus(OrderStatus.PAID, OrderStatus.PROCESSING);
        status = OrderStatus.PROCESSING;
        processingAt = occurredAt;
    }

    public void markShipped(Instant occurredAt) {
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

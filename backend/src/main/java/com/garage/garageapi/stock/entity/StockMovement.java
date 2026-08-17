package com.garage.garageapi.stock.entity;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "stock_movements",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_movement_reference_product_type",
                columnNames = {"product_id", "type", "reference_type", "reference_id"}),
        indexes = {
                @Index(name = "ix_stock_movements_product", columnList = "product_id"),
                @Index(name = "ix_stock_movements_type", columnList = "type"),
                @Index(name = "ix_stock_movements_created", columnList = "created_at")
        })
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockMovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "previous_stock", nullable = false)
    private int previousStock;

    @Column(name = "new_stock", nullable = false)
    private int newStock;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 40)
    private StockReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() { }

    public StockMovement(Product product, StockMovementType type, int quantity,
                         int previousStock, int newStock, String reason,
                         StockReferenceType referenceType, Long referenceId,
                         User performedByUser) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantidade da movimentação deve ser positiva");
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.performedByUser = performedByUser;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public StockMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getPreviousStock() { return previousStock; }
    public int getNewStock() { return newStock; }
    public String getReason() { return reason; }
    public StockReferenceType getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public User getPerformedByUser() { return performedByUser; }
    public Instant getCreatedAt() { return createdAt; }
}

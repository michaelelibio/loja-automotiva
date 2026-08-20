package com.garage.garageapi.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Column(length = 5000)
    private String longDescription;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(unique = true, length = 100)
    private String sku;

    @Column(name = "supplier", length = 50)
    private String supplier;

    @Column(name = "supplier_product_id", unique = true, length = 150)
    private String supplierProductId;

    @Column(name = "supplier_cost_usd", precision = 19, scale = 4)
    private BigDecimal supplierCostUsd;

    @Column(name = "supplier_exchange_rate", precision = 19, scale = 6)
    private BigDecimal supplierExchangeRate;

    @Column(name = "supplier_cost_updated_at")
    private Instant supplierCostUpdatedAt;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'SINGLE'")
    private ProductType productType = ProductType.SINGLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 30)
    @ColumnDefault("'LOCAL_STOCK'")
    private FulfillmentType fulfillmentType = FulfillmentType.LOCAL_STOCK;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
    }

    public Product(String name, String slug, String description, String longDescription,
                   BigDecimal price, BigDecimal oldPrice, String category, Integer stockQuantity,
                   String imageUrl, Boolean active) {
        this(name, slug, description, longDescription, price, oldPrice, category, stockQuantity,
                imageUrl, active, ProductType.SINGLE);
    }

    public Product(String name, String slug, String description, String longDescription,
                   BigDecimal price, BigDecimal oldPrice, String category, Integer stockQuantity,
                   String imageUrl, Boolean active, ProductType productType) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.longDescription = longDescription;
        this.price = price;
        this.oldPrice = oldPrice;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
        this.active = active;
        this.productType = productType == null ? ProductType.SINGLE : productType;
    }

    public void update(String name, String slug, String description, String longDescription,
                       BigDecimal price, BigDecimal oldPrice, String category,
                       String imageUrl, Boolean active, ProductType productType) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.longDescription = longDescription;
        this.price = price;
        this.oldPrice = oldPrice;
        this.category = category;
        this.imageUrl = imageUrl;
        this.active = active;
        this.productType = productType == null ? this.productType : productType;
    }

    public void updateAdmin(String name, String slug, String description, String longDescription,
                            BigDecimal price, BigDecimal oldPrice, BigDecimal costPrice,
                            String category, String imageUrl, Boolean active,
                            ProductType productType, String sku) {
        update(name, slug, description, longDescription, price, oldPrice, category,
                imageUrl, active, productType);
        this.costPrice = costPrice;
        this.sku = sku;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void decreaseStock(int quantity) {
        requireLocalStock();
        if (quantity < 1 || quantity > stockQuantity) {
            throw new IllegalArgumentException("Quantidade inválida para baixa de estoque");
        }
        stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        requireLocalStock();
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantidade inválida para entrada de estoque");
        }
        stockQuantity = Math.addExact(stockQuantity, quantity);
    }

    public void setActive(boolean active) { this.active = active; }

    public void configureFulfillment(FulfillmentType fulfillmentType) {
        this.fulfillmentType = fulfillmentType == null ? FulfillmentType.LOCAL_STOCK : fulfillmentType;
    }

    public boolean requiresLocalStock() {
        return fulfillmentType == FulfillmentType.LOCAL_STOCK;
    }

    public boolean isAvailableForSale() {
        return Boolean.TRUE.equals(active) && (!requiresLocalStock() || stockQuantity > 0);
    }

    public boolean canFulfill(int quantity) {
        return quantity > 0 && Boolean.TRUE.equals(active)
                && (!requiresLocalStock() || stockQuantity >= quantity);
    }

    private void requireLocalStock() {
        if (!requiresLocalStock()) {
            throw new IllegalArgumentException("Estoque local não se aplica a produto dropshipping");
        }
    }

    public void linkSupplier(String supplier, String supplierProductId, BigDecimal supplierCostUsd,
                             BigDecimal supplierExchangeRate, Instant supplierCostUpdatedAt) {
        this.supplier = supplier;
        this.supplierProductId = supplierProductId;
        this.supplierCostUsd = supplierCostUsd;
        this.supplierExchangeRate = supplierExchangeRate;
        this.supplierCostUpdatedAt = supplierCostUpdatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getLongDescription() { return longDescription; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public String getSku() { return sku; }
    public String getSupplier() { return supplier; }
    public String getSupplierProductId() { return supplierProductId; }
    public BigDecimal getSupplierCostUsd() { return supplierCostUsd; }
    public BigDecimal getSupplierExchangeRate() { return supplierExchangeRate; }
    public Instant getSupplierCostUpdatedAt() { return supplierCostUpdatedAt; }
    public String getCategory() { return category; }
    public Integer getStockQuantity() { return stockQuantity; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getActive() { return active; }
    public ProductType getProductType() { return productType; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProductVariant> getVariants() { return variants; }
}

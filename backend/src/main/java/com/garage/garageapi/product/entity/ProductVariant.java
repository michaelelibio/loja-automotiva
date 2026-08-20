package com.garage.garageapi.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_variants_supplier_variant",
        columnNames = {"supplier", "supplier_variant_id"}
))
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String supplier;

    @Column(name = "supplier_variant_id", nullable = false, length = 150)
    private String supplierVariantId;

    @Column(name = "supplier_product_id", nullable = false, length = 150)
    private String supplierProductId;

    @Column(name = "supplier_sku", length = 150)
    private String supplierSku;

    @Column(length = 500)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> attributes = Map.of();

    @Column(name = "supplier_cost", precision = 19, scale = 4)
    private BigDecimal supplierCost;

    @Column(name = "supplier_cost_currency", nullable = false, length = 3)
    private String supplierCostCurrency;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "weight_grams", precision = 19, scale = 4)
    private BigDecimal weightGrams;

    @Column(name = "length_mm", precision = 19, scale = 4)
    private BigDecimal lengthMm;

    @Column(name = "width_mm", precision = 19, scale = 4)
    private BigDecimal widthMm;

    @Column(name = "height_mm", precision = 19, scale = 4)
    private BigDecimal heightMm;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductVariant() {}

    public ProductVariant(Product product, String supplier, String supplierVariantId,
                          String supplierProductId, String supplierSku, String name,
                          Map<String, String> attributes, BigDecimal supplierCost,
                          String supplierCostCurrency, String imageUrl, BigDecimal weightGrams,
                          BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm) {
        this.product = product;
        updateSupplierData(supplier, supplierVariantId, supplierProductId, supplierSku, name,
                attributes, supplierCost, supplierCostCurrency, imageUrl, weightGrams,
                lengthMm, widthMm, heightMm);
    }

    public void updateSupplierData(String supplier, String supplierVariantId,
                                   String supplierProductId, String supplierSku, String name,
                                   Map<String, String> attributes, BigDecimal supplierCost,
                                   String supplierCostCurrency, String imageUrl,
                                   BigDecimal weightGrams, BigDecimal lengthMm,
                                   BigDecimal widthMm, BigDecimal heightMm) {
        this.supplier = supplier;
        this.supplierVariantId = supplierVariantId;
        this.supplierProductId = supplierProductId;
        this.supplierSku = supplierSku;
        this.name = name;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.supplierCost = supplierCost;
        this.supplierCostCurrency = supplierCostCurrency;
        this.imageUrl = imageUrl;
        this.weightGrams = weightGrams;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSupplier() { return supplier; }
    public String getSupplierVariantId() { return supplierVariantId; }
    public String getSupplierProductId() { return supplierProductId; }
    public String getSupplierSku() { return supplierSku; }
    public String getName() { return name; }
    public Map<String, String> getAttributes() { return attributes; }
    public BigDecimal getSupplierCost() { return supplierCost; }
    public String getSupplierCostCurrency() { return supplierCostCurrency; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public BigDecimal getLengthMm() { return lengthMm; }
    public BigDecimal getWidthMm() { return widthMm; }
    public BigDecimal getHeightMm() { return heightMm; }
    public Boolean getActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
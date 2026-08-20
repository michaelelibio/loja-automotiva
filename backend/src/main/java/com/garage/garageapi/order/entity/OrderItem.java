package com.garage.garageapi.order.entity;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.entity.FulfillmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "product_slug", nullable = false, length = 180)
    private String productSlug;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", length = 30)
    private FulfillmentType fulfillmentType;

    @Column(name = "supplier", length = 50)
    private String supplier;

    @Column(name = "supplier_product_id", length = 150)
    private String supplierProductId;

    @Column(name = "supplier_variant_id", length = 150)
    private String supplierVariantId;

    @Column(name = "supplier_sku", length = 150)
    private String supplierSku;

    @Column(name = "product_variant_id")
    private Long productVariantId;

    @Column(name = "variant_name", length = 500)
    private String variantName;

    @Column(name = "supplier_cost", precision = 19, scale = 4)
    private BigDecimal supplierCost;

    @Column(name = "supplier_cost_currency", length = 3)
    private String supplierCostCurrency;

    @Column(name = "weight_grams", precision = 19, scale = 4)
    private BigDecimal weightGrams;

    @Column(name = "length_mm", precision = 19, scale = 4)
    private BigDecimal lengthMm;

    @Column(name = "width_mm", precision = 19, scale = 4)
    private BigDecimal widthMm;

    @Column(name = "height_mm", precision = 19, scale = 4)
    private BigDecimal heightMm;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() { }

    public OrderItem(Order order, Product product, int quantity, BigDecimal unitPrice,
                     BigDecimal subtotal) {
        this(order, product, null, quantity, unitPrice, subtotal);
    }

    public OrderItem(Order order, Product product, ProductVariant variant, int quantity,
                     BigDecimal unitPrice, BigDecimal subtotal) {
        this.order = order;
        this.productId = product.getId();
        this.productName = product.getName();
        this.productSlug = product.getSlug();
        this.unitPrice = unitPrice;
        this.unitCost = product.getCostPrice();
        this.fulfillmentType = product.getFulfillmentType();
        this.supplier = product.getSupplier();
        this.supplierProductId = product.getSupplierProductId();
        if (variant != null) {
            this.productVariantId = variant.getId();
            this.supplierVariantId = variant.getSupplierVariantId();
            this.supplierSku = variant.getSupplierSku();
            this.variantName = variant.getName();
            this.supplierCost = variant.getSupplierCost();
            this.supplierCostCurrency = variant.getSupplierCostCurrency();
            this.weightGrams = variant.getWeightGrams();
            this.lengthMm = variant.getLengthMm();
            this.widthMm = variant.getWidthMm();
            this.heightMm = variant.getHeightMm();
        } else if (product.getSupplierCostUsd() != null) {
            this.supplierCost = product.getSupplierCostUsd();
            this.supplierCostCurrency = "USD";
        }
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductSlug() { return productSlug; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getSubtotal() { return subtotal; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public String getSupplier() { return supplier; }
    public String getSupplierProductId() { return supplierProductId; }
    public String getSupplierVariantId() { return supplierVariantId; }
    public String getSupplierSku() { return supplierSku; }
    public Long getProductVariantId() { return productVariantId; }
    public String getVariantName() { return variantName; }
    public BigDecimal getSupplierCost() { return supplierCost; }
    public String getSupplierCostCurrency() { return supplierCostCurrency; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public BigDecimal getLengthMm() { return lengthMm; }
    public BigDecimal getWidthMm() { return widthMm; }
    public BigDecimal getHeightMm() { return heightMm; }
}

package com.garage.garageapi.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

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
        this.productType = productType == null ? this.productType : productType;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getLongDescription() { return longDescription; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public String getCategory() { return category; }
    public Integer getStockQuantity() { return stockQuantity; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getActive() { return active; }
    public ProductType getProductType() { return productType; }
}

package com.garage.garageapi.product.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_media")
public class ProductMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductMediaType type;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductMediaSource source;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductMedia() {}

    public ProductMedia(Product product, ProductMediaType type, String url, String sourceUrl,
                        int position, String altText, ProductMediaSource source) {
        this.product = product;
        this.type = type;
        this.source = source;
        update(url, sourceUrl, position, altText, true);
    }

    public void update(String url, String sourceUrl, int position, String altText, boolean active) {
        this.url = url;
        this.sourceUrl = sourceUrl;
        this.position = position;
        this.altText = altText;
        this.active = active;
    }

    public void setActive(boolean active) { this.active = active; }

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
    public ProductMediaType getType() { return type; }
    public String getUrl() { return url; }
    public String getSourceUrl() { return sourceUrl; }
    public Integer getPosition() { return position; }
    public String getAltText() { return altText; }
    public ProductMediaSource getSource() { return source; }
    public Boolean getActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

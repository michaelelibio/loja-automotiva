package com.garage.garageapi.vehicle.entity;

import com.garage.garageapi.user.entity.User;
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

import java.time.Instant;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(name = "vehicle_year", nullable = false)
    private Integer year;

    @Column(length = 150)
    private String version;

    @Column(name = "license_plate", length = 7)
    private String licensePlate;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Vehicle() { }

    public Vehicle(User user, String brand, String model, Integer year, String version,
                   String licensePlate, boolean primary) {
        this(user, brand, model, year, version, licensePlate, primary, null);
    }

    public Vehicle(User user, String brand, String model, Integer year, String version,
                   String licensePlate, boolean primary, String imageUrl) {
        this.user = user;
        update(brand, model, year, version, licensePlate, primary, imageUrl);
    }

    public void update(String brand, String model, Integer year, String version,
                       String licensePlate, boolean primary) {
        update(brand, model, year, version, licensePlate, primary, imageUrl);
    }

    public void update(String brand, String model, Integer year, String version,
                       String licensePlate, boolean primary, String imageUrl) {
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.version = version;
        this.licensePlate = licensePlate;
        this.primary = primary;
        this.imageUrl = imageUrl;
    }

    public void setPrimary(boolean primary) { this.primary = primary; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public Integer getYear() { return year; }
    public String getVersion() { return version; }
    public String getLicensePlate() { return licensePlate; }
    public String getImageUrl() { return imageUrl; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.garage.garageapi.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(length = 255)
    private String passwordHash;

    @Column(unique = true, length = 255)
    private String googleSubject;

    @Column(length = 1000)
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User local(String name, String email, String passwordHash) {
        return new User(name, email, passwordHash, null, null, AuthProvider.LOCAL);
    }

    public static User google(String name, String email, String googleSubject, String pictureUrl) {
        return new User(name, email, null, googleSubject, pictureUrl, AuthProvider.GOOGLE);
    }

    private User(String name, String email, String passwordHash, String googleSubject,
                 String pictureUrl, AuthProvider authProvider) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleSubject = googleSubject;
        this.pictureUrl = pictureUrl;
        this.authProvider = authProvider;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getGoogleSubject() { return googleSubject; }
    public String getPictureUrl() { return pictureUrl; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateName(String name) {
        this.name = name;
    }
}

package com.garage.garageapi.user.dto;

import com.garage.garageapi.user.entity.AuthProvider;
import com.garage.garageapi.user.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String pictureUrl,
        AuthProvider authProvider,
        boolean emailVerified,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPictureUrl(),
                user.getAuthProvider(), user.isEmailVerified(), user.isActive(), user.getCreatedAt(),
                user.getUpdatedAt());
    }
}

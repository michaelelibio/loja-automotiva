package com.garage.garageapi.auth.dto;

import com.garage.garageapi.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}

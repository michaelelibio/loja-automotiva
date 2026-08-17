package com.garage.garageapi.integration.cj.client;

import java.time.Instant;

public record CjTokenData(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {}

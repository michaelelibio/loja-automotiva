package com.garage.garageapi.integration.aliexpress;

public record AliExpressTokenData(String accessToken, String refreshToken,
                                 Long expiresIn, Long refreshExpiresIn,
                                 Long expireTime, Long refreshTokenValidTime,
                                 String userId, String accountId) {
    @Override
    public String toString() {
        return "AliExpressTokenData[tokens=REDACTED, identifiers=REDACTED]";
    }
}

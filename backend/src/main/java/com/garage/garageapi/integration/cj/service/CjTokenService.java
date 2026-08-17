package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.client.CjApiClient;
import com.garage.garageapi.integration.cj.client.CjTokenData;
import com.garage.garageapi.integration.cj.config.CjProperties;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class CjTokenService {
    private static final Logger logger = LoggerFactory.getLogger(CjTokenService.class);

    private final CjApiClient client;
    private final String apiKey;
    private final Duration expirySkew;
    private final Clock clock;
    private volatile CjTokenData current;

    @Autowired
    public CjTokenService(CjApiClient client, CjProperties properties) {
        this(client, properties.getApiKey(), properties.getTokenExpirySkew(), Clock.systemUTC());
    }

    CjTokenService(CjApiClient client, String apiKey, Duration expirySkew, Clock clock) {
        this.client = client;
        this.apiKey = apiKey;
        this.expirySkew = expirySkew;
        this.clock = clock;
    }

    public synchronized String getValidAccessToken() {
        requireConfiguration();
        Instant threshold = clock.instant().plus(expirySkew);
        if (current != null && current.accessTokenExpiresAt().isAfter(threshold)) {
            return current.accessToken();
        }
        if (current != null && current.refreshTokenExpiresAt().isAfter(threshold)) {
            try {
                current = client.refresh(current.refreshToken());
                logger.info("CJ Dropshipping access token renewed using refresh token");
                return current.accessToken();
            } catch (CjIntegrationException exception) {
                if (exception.getReason() != CjIntegrationException.Reason.AUTHENTICATION) throw exception;
                logger.warn("CJ Dropshipping refresh token rejected; requesting a new access token");
            }
        }
        current = client.authenticate(apiKey);
        logger.info("CJ Dropshipping access token obtained");
        return current.accessToken();
    }

    public synchronized void invalidateAccessToken(String rejectedToken) {
        if (current != null && current.accessToken().equals(rejectedToken)) {
            current = new CjTokenData(current.accessToken(), Instant.EPOCH,
                    current.refreshToken(), current.refreshTokenExpiresAt());
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(apiKey)) {
            throw new CjIntegrationException("Integração com a CJ não está configurada",
                    CjIntegrationException.Reason.NOT_CONFIGURED);
        }
    }
}

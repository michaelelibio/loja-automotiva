package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.client.CjApiClient;
import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.springframework.stereotype.Service;

@Service
public class CjProductService {
    private final CjApiClient client;
    private final CjTokenService tokenService;

    public CjProductService(CjApiClient client, CjTokenService tokenService) {
        this.client = client;
        this.tokenService = tokenService;
    }

    public CjProductResponse list(String keyword, int page, int size) {
        String token = tokenService.getValidAccessToken();
        try {
            return client.listProducts(token, keyword, page, size);
        } catch (CjIntegrationException exception) {
            if (exception.getReason() != CjIntegrationException.Reason.AUTHENTICATION) throw exception;
            tokenService.invalidateAccessToken(token);
            String renewedToken = tokenService.getValidAccessToken();
            return client.listProducts(renewedToken, keyword, page, size);
        }
    }
    public CjProductResponse.Product get(String productId) {
        String token = tokenService.getValidAccessToken();

        try {
            return client.getProduct(token, productId);
        } catch (CjIntegrationException exception) {
            if (exception.getReason() != CjIntegrationException.Reason.AUTHENTICATION) {
                throw exception;
            }

            tokenService.invalidateAccessToken(token);

            String renewedToken = tokenService.getValidAccessToken();

            return client.getProduct(renewedToken, productId);
        }
    }
}

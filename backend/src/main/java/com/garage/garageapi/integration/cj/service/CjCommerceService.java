package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.client.CjApiClient;
import com.garage.garageapi.integration.cj.dto.CjFreightResponse;
import com.garage.garageapi.integration.cj.dto.CjVariantInventoryResponse;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderRequest;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class CjCommerceService {
    private final CjApiClient client;
    private final CjTokenService tokenService;

    public CjCommerceService(CjApiClient client, CjTokenService tokenService) {
        this.client = client;
        this.tokenService = tokenService;
    }

    public CjVariantInventoryResponse inventory(String variantId) {
        return authenticated(token -> client.getVariantInventory(token, variantId));
    }

    public CjFreightResponse freight(String originCountry, String destinationCountry,
                                     String zipCode, List<Map<String, Object>> products) {
        return authenticated(token -> client.calculateFreight(token, originCountry,
                destinationCountry, zipCode, products));
    }

    public CjCreateOrderResponse createOrder(CjCreateOrderRequest request) {
        return authenticated(token -> client.createOrder(token, request));
    }

    private <T> T authenticated(Function<String, T> operation) {
        String token = tokenService.getValidAccessToken();
        try {
            return operation.apply(token);
        } catch (CjIntegrationException exception) {
            if (exception.getReason() != CjIntegrationException.Reason.AUTHENTICATION) throw exception;
            tokenService.invalidateAccessToken(token);
            return operation.apply(tokenService.getValidAccessToken());
        }
    }
}

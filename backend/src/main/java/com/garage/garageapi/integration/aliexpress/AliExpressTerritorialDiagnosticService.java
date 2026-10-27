package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Service;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

@Service
public class AliExpressTerritorialDiagnosticService {
    private final AliExpressTokenService tokens;
    private final AliExpressTerritorialClient client;
    public AliExpressTerritorialDiagnosticService(AliExpressTokenService tokens,AliExpressTerritorialClient client) {
        this.tokens=tokens;this.client=client;
    }
    public AliExpressTerritorialDiagnosticResult run() {
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) {
            return AliExpressTerritorialDiagnosticResult.failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE");
        }
        return client.query(token);
    }
}

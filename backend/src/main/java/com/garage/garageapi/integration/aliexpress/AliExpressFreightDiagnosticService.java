package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Service;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.*;

@Service
public class AliExpressFreightDiagnosticService {
    private final AliExpressTokenService tokens;
    private final AliExpressFreightDiagnosticClient client;
    public AliExpressFreightDiagnosticService(AliExpressTokenService tokens, AliExpressFreightDiagnosticClient client) {
        this.tokens=tokens;this.client=client;
    }
    public AliExpressFreightDiagnosticResult run() {
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) {
            return failure(Status.LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE");
        }
        return client.query(token);
    }
}

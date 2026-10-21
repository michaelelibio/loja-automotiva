package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Clock;

/** Compatibility facade: ADM and checkout share the same real transport/parser. */
@Component
public class AliExpressFreightDiagnosticClient {
    private final AliExpressDsFreightClient client;
    @Autowired
    public AliExpressFreightDiagnosticClient(AliExpressDsFreightClient client) { this.client=client; }
    AliExpressFreightDiagnosticClient(RestClient http,String key,String secret,Clock clock) {
        this(new AliExpressDsFreightClient(http,key,secret,clock));
    }
    public record Input(String productId,String skuId,int quantity,String destinationCountry) {}
    public AliExpressFreightDiagnosticResult query(String token,Input input) {
        return client.query(token,input == null ? null : new AliExpressDsFreightClient.Input(
                input.productId(),input.skuId(),input.quantity(),input.destinationCountry()));
    }
}

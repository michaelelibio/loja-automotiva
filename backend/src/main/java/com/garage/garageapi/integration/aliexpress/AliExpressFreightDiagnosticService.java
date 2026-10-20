package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Service;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.*;

@Service
public class AliExpressFreightDiagnosticService {
    private final AliExpressTokenService tokens;
    private final AliExpressFreightDiagnosticClient client;
    private final AliExpressProductClient products;
    private final AliExpressProductNormalizer normalizer;
    public AliExpressFreightDiagnosticService(AliExpressTokenService tokens, AliExpressFreightDiagnosticClient client,
            AliExpressProductClient products, AliExpressProductNormalizer normalizer) {
        this.tokens=tokens;this.client=client;this.products=products;this.normalizer=normalizer;
    }
    public record Option(String skuId, int availableStock) {}
    public record Preparation(String productId, List<Option> variants) {}

    public Preparation prepare(String id) {
        String productId=AliExpressProductInput.productId(id);
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) { throw new AliExpressIntegrationException(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED); }
        return options(load(productId,token));
    }
    private AliExpressProductPreview load(String id, String token) {
        // Same persisted OAuth, no refresh; no shipping-origin inference is needed by ds.freight.query.
        return normalizer.normalize(products.get(id,"BR","BRL","en",token),id,"BR");
    }
    private Preparation options(AliExpressProductPreview product) {
        return new Preparation(product.productId(),product.variants().stream()
                .map(v -> new Option(v.skuId(),v.availableStock())).toList());
    }
    public AliExpressFreightDiagnosticResult run(AliExpressFreightDiagnosticRequest request) {
        if (request == null || request.productId() == null || !request.productId().matches("[0-9]{1,18}")
                || request.skuId() == null || !request.skuId().matches("[0-9]{1,30}")
                || request.quantity() == null || request.quantity()<1 || request.quantity()>100)
            return failure(Status.PARAMETER_ERROR,"INVALID_TEST_INPUT");
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) { return failure(Status.LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE"); }
        final Preparation product;
        try { product=options(load(request.productId(),token)); }
        catch (AliExpressIntegrationException ex) {
            return failure(switch(ex.getReason()) {
                case PERMISSION -> Status.PERMISSION_DENIED;
                case AUTHORIZATION_REQUIRED -> Status.TOKEN_REJECTED;
                case NOT_FOUND, UNAVAILABLE, COUNTRY_REJECTED -> Status.PRODUCT_UNAVAILABLE;
                case RATE_LIMIT -> Status.RATE_LIMITED;
                case NOT_CONFIGURED -> Status.NOT_CONFIGURED;
                case TIMEOUT, COMMUNICATION -> Status.COMMUNICATION_ERROR;
                default -> Status.INVALID_RESPONSE;
            },"PRODUCT_LOOKUP_FAILED");
        }
        Option selected=product.variants().stream().filter(v -> request.skuId().equals(v.skuId())).findFirst().orElse(null);
        if (selected == null) return failure(Status.PARAMETER_ERROR,"SKU_NOT_IN_PRODUCT");
        if (selected.availableStock()<request.quantity()) return failure(Status.PRODUCT_UNAVAILABLE,"INSUFFICIENT_SUPPLIER_STOCK");
        return client.query(token,new AliExpressFreightDiagnosticClient.Input(product.productId(),selected.skuId(),request.quantity(),"BR"));
    }
}

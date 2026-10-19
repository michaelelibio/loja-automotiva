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
    public record Option(String skuId, String origin, String originSource, int availableStock) {}
    public record Preparation(String productId, List<Option> variants) {}

    public Preparation prepare(String id) {
        String productId=AliExpressProductInput.productId(id);
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) { throw new AliExpressIntegrationException(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED); }
        return options(load(productId,token));
    }
    private AliExpressProductPreview load(String id, String token) {
        // Same persisted OAuth, no refresh; English labels allow explicit Ships From recognition.
        return normalizer.normalize(products.get(id,"BR","BRL","en",token),id,"BR");
    }
    private Preparation options(AliExpressProductPreview product) {
        return new Preparation(product.productId(),product.variants().stream().map(v -> {
            String origin=origin(v.attributes());
            return new Option(v.skuId(),origin,origin == null ? "UNCONFIRMED" : "SKU_SHIPS_FROM",v.availableStock());
        }).toList());
    }
    static String origin(Map<String,String> attributes) {
        Set<String> origins=new HashSet<>();
        for (var entry:attributes.entrySet()) {
            // Never infer shipping origin from store country, destination, SKU ID or price.
            if (!entry.getKey().endsWith(":name") || !"Ships From".equalsIgnoreCase(entry.getValue())) continue;
            String value=attributes.get(entry.getKey().substring(0,entry.getKey().length()-5));
            if (value == null) return null;
            String found=null;
            for (String code:Locale.getISOCountries()) {
                if (code.equals(value) || new Locale("",code).getDisplayCountry(Locale.ENGLISH).equals(value)) {
                    found=code;break;
                }
            }
            if (found == null) return null;
            origins.add(found);
        }
        return origins.size()==1 ? origins.iterator().next() : null;
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
        if (selected.origin() == null) return failure(Status.PARAMETER_ERROR,"SHIPPING_ORIGIN_UNCONFIRMED");
        if (selected.availableStock()<request.quantity()) return failure(Status.PRODUCT_UNAVAILABLE,"INSUFFICIENT_SUPPLIER_STOCK");
        return client.query(token,new AliExpressFreightDiagnosticClient.Input(product.productId(),selected.skuId(),request.quantity(),selected.origin()));
    }
}

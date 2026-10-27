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
    public record Preparation(String productId, List<Option> variants, String storeCountryCode) {}

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
        String storeCountry=product.store().get("store_country_code");
        if(!Arrays.asList(Locale.getISOCountries()).contains(storeCountry)) storeCountry=null;
        return new Preparation(product.productId(),product.variants().stream()
                .map(v -> new Option(v.skuId(),v.availableStock())).toList(),storeCountry);
    }
    public AliExpressFreightDiagnosticResult run(AliExpressFreightDiagnosticRequest request) {
        var probe=prepareProbe(request);
        return probe.failure()!=null?probe.failure():client.query(probe.token(),probe.input());
    }
    public AliExpressFreightProvinceComparison compareProvince(AliExpressFreightDiagnosticRequest request) {
        var probe=prepareProbe(request);
        if(probe.failure()!=null) return AliExpressFreightProvinceComparison.of("NOT_EXECUTED",probe.failure().safeCode(),null,null);
        var baseline=client.queryObserved(probe.token(),probe.input());
        // Two planned observations, not retries. Abort if baseline cannot safely support the second call.
        if (baseline.result().status()!=Status.SUCCESS && !Set.of("AMBIGUOUS_MONETARY_UNIT","CONFLICTING_MONETARY_FIELDS","NO_DELIVERY_METHODS").contains(baseline.result().safeCode()))
            return AliExpressFreightProvinceComparison.of("STOPPED_AFTER_BASELINE",baseline.result().safeCode(),baseline,null);
        var input=probe.input();
        var province=client.queryObserved(probe.token(),new AliExpressFreightDiagnosticClient.Input(
                input.productId(),input.skuId(),input.quantity(),"BR","Santa Catarina"));
        return AliExpressFreightProvinceComparison.of("COMPLETED","COMPARISON_COLLECTED",baseline,province);
    }
    private record ProbeContext(String token,AliExpressFreightDiagnosticClient.Input input,AliExpressFreightDiagnosticResult failure) {
        @Override public String toString() { return "FreightProbeContext[redacted]"; }
    }
    private static ProbeContext failed(Status status,String code) { return new ProbeContext(null,null,failure(status,code)); }
    private ProbeContext prepareProbe(AliExpressFreightDiagnosticRequest request) {
        if (request == null || request.productId() == null || !request.productId().matches("[0-9]{1,18}")
                || request.skuId() == null || !request.skuId().matches("[0-9]{1,30}")
                || request.quantity() == null || request.quantity()<1 || request.quantity()>100)
            return failed(Status.PARAMETER_ERROR,"INVALID_TEST_INPUT");
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) { return failed(Status.LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE"); }
        final Preparation product;
        try { product=options(load(request.productId(),token)); }
        catch (AliExpressIntegrationException ex) {
            return failed(switch(ex.getReason()) {
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
        if (selected == null) return failed(Status.PARAMETER_ERROR,"SKU_NOT_IN_PRODUCT");
        if (selected.availableStock()<request.quantity()) return failed(Status.PRODUCT_UNAVAILABLE,"INSUFFICIENT_SUPPLIER_STOCK");
        return new ProbeContext(token,new AliExpressFreightDiagnosticClient.Input(product.productId(),selected.skuId(),request.quantity(),"BR"),null);
    }
}

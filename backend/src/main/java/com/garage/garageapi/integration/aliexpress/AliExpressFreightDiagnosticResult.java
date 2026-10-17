package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.util.List;

public record AliExpressFreightDiagnosticResult(Status status, String safeCode, List<Method> methods,
        List<String> limitations) {
    public enum Status {
        SUCCESS, PERMISSION_DENIED, TOKEN_REJECTED, PRODUCT_UNAVAILABLE, PARAMETER_ERROR,
        COMMUNICATION_ERROR, INVALID_RESPONSE, PROVIDER_REJECTED, LOCAL_AUTHORIZATION_UNAVAILABLE,
        NOT_CONFIGURED, RATE_LIMITED, PROTOCOL_ERROR
    }
    public record Method(String serviceName, BigDecimal amount, String currency, String estimatedDeliveryTime) {}
    public AliExpressFreightDiagnosticResult {
        methods = List.copyOf(methods); limitations = List.copyOf(limitations);
    }
    static AliExpressFreightDiagnosticResult result(Status status, String code, List<Method> methods) {
        return new AliExpressFreightDiagnosticResult(status,code,methods,List.of(
                "CN_ORIGIN_UNCONFIRMED", "SKU_ID_NOT_DOCUMENTED_NOT_SENT", "NO_POSTAL_CODE_QUOTE",
                "ESTIMATED_DELIVERY_NOT_GUARANTEED", "DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED"));
    }
    static AliExpressFreightDiagnosticResult failure(Status status, String code) { return result(status,code,List.of()); }
}

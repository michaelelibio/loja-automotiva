package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.util.List;

public record AliExpressFreightDiagnosticResult(Status status, String safeCode, List<Method> methods,
        List<String> limitations, List<MoneyField> monetaryStructure) {
    public record MoneyField(int optionIndex, String path, boolean present, String jsonType,
            String currency, String documentedScale, String safeValue, String comparison) {
        @com.fasterxml.jackson.annotation.JsonProperty("syntacticallyValidNumber")
        public boolean syntacticallyValidNumber() {
            if (!path.equals("shipping_fee_cent") || safeValue == null) return false;
            try { new BigDecimal(safeValue); return true; } catch (NumberFormatException ex) { return false; }
        }
        public MoneyField(int index, String path, boolean present, String type, String currency, String scale, String value) {
            this(index,path,present,type,currency,scale,value,null);
        }
    }
    public AliExpressFreightDiagnosticResult(Status status, String code, List<Method> methods, List<String> limitations) {
        this(status,code,methods,limitations,List.of());
    }
    @com.fasterxml.jackson.annotation.JsonProperty("commercialAuthorization")
    public String commercialAuthorization() { return "NOT_AUTHORIZED_ADDRESS_AND_SCALE_REQUIRE_CONFIRMATION"; }
    public AliExpressFreightDiagnosticResult withMonetaryStructure(List<MoneyField> fields) {
        return new AliExpressFreightDiagnosticResult(status,safeCode,methods,limitations,fields);
    }
    public enum Status {
        MONEY_SCALE_UNCONFIRMED, SUCCESS, PERMISSION_DENIED, TOKEN_REJECTED, PRODUCT_UNAVAILABLE, PARAMETER_ERROR,
        COMMUNICATION_ERROR, INVALID_RESPONSE, PROVIDER_REJECTED, LOCAL_AUTHORIZATION_UNAVAILABLE,
        NOT_CONFIGURED, RATE_LIMITED, PROTOCOL_ERROR
    }
    public record Method(String serviceName,
            @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal amount,
            String currency, String estimatedDeliveryTime, String company, String originCountry) {
        public Method(String serviceName,BigDecimal amount,String currency,String estimate) {
            this(serviceName,amount,currency,estimate,null,null);
        }
    }
    public AliExpressFreightDiagnosticResult {
        methods = List.copyOf(methods); limitations = List.copyOf(limitations); monetaryStructure = List.copyOf(monetaryStructure);
    }
    static AliExpressFreightDiagnosticResult result(Status status, String code, List<Method> methods) {
        return new AliExpressFreightDiagnosticResult(status,code,methods,List.of(
                "APP_API_PERMISSION_NOT_PRECONFIRMED", "COUNTRY_ONLY_BR_DIAGNOSTIC", "NO_POSTAL_CODE_QUOTE",
                "ESTIMATED_DELIVERY_NOT_GUARANTEED", "DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED"));
    }
    static AliExpressFreightDiagnosticResult failure(Status status, String code) { return result(status,code,List.of()); }
}

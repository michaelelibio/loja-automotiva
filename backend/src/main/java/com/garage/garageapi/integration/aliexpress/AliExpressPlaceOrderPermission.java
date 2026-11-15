package com.garage.garageapi.integration.aliexpress;
import org.springframework.stereotype.Component;
/** Explicit, app-bound operational evidence; the provider remains the authority on current access. */
@Component
public class AliExpressPlaceOrderPermission {
    @org.springframework.beans.factory.annotation.Value("${aliexpress.app-key:}") private String appKey = "";
    @org.springframework.beans.factory.annotation.Value("${aliexpress.orders.permission-app-key-sha256:}") private String appHash = "";
    @org.springframework.beans.factory.annotation.Value("${aliexpress.orders.permission-evidence:}") private String evidence = "";
    public boolean verified(){return !appKey.isBlank() && appHash.matches("[a-f0-9]{64}")
            && appHash.equals(AliExpressOAuthStateService.hash(appKey)) && !evidence.isBlank();}
    public String status(){return verified() ? "EVIDENCE_CONFIGURED" : "NOT_VERIFIED";}
}

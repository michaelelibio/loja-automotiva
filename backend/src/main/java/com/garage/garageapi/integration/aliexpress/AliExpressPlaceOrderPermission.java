package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Component;

/** No safe read-only proof of method permission has been homologated for this application. */
@Component
public class AliExpressPlaceOrderPermission {
    public boolean verified() { return false; }
    public String status() { return "NOT_VERIFIED"; }
}

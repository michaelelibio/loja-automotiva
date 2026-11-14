package com.garage.garageapi.integration.aliexpress;
import org.springframework.stereotype.Component;
/** Normal customer production flow stays closed. The explicit #19 attempt is decided by the provider. */
@Component
public class AliExpressPlaceOrderPermission {
    public boolean verified(){return false;}
    public String status(){return "NOT_VERIFIED";}
}

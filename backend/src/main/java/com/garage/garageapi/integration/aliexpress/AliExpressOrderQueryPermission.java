package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Component;

/** Permission for read-only order lookup must be homologated separately from placeorder. */
@Component
public class AliExpressOrderQueryPermission {
    public boolean verified() { return false; }
}

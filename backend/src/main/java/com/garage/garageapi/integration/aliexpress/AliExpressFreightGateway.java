package com.garage.garageapi.integration.aliexpress;

import java.util.List;

/** Internal boundary, NOT the provider's HTTP contract. No live implementation yet.
 * An adapter must resolve trusted origins and map verified provider fields before
 * it can implement this interface. It must never use browser-supplied prices.
 */
public interface AliExpressFreightGateway {
    List<AliExpressFreightQuote.Method> calculate(AliExpressFreightQuote.Context context);
}

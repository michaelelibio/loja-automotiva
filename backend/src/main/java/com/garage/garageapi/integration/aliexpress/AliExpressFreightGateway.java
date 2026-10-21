package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shipping.provider.ShippingProvider;
import java.util.List;

/** Internal boundary; local IDs are resolved by the adapter, never taken as supplier IDs. */
public interface AliExpressFreightGateway {
    Evaluation calculate(ShippingProvider.Request request);
    enum Coverage { COUNTRY_ONLY }
    record LineQuote(Long productId,Long variantId,int quantity,List<AliExpressFreightDiagnosticResult.Method> methods) {
        public LineQuote { methods=List.copyOf(methods); }
    }
    record Evaluation(Coverage coverage,List<LineQuote> lines) {
        public Evaluation { lines=List.copyOf(lines); }
    }
}

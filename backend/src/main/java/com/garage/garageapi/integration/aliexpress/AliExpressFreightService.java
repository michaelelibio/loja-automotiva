package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Fail closed until the application's freight contract supports a confirmed
 * destination and SKU. The public freight.calculate contract does not document
 * either postal code or SKU, and requires a trusted dispatch country.
 * Never substitute local shipping or treat a country-level estimate as a quote.
 */
@Service
public class AliExpressFreightService {
    public static final String UNAVAILABLE =
            "O frete dos produtos AliExpress ainda não pode ser confirmado. A compra está temporariamente indisponível para esses itens.";
    private final AliExpressFreightGateway gateway;
    private final java.time.Clock clock;

    public AliExpressFreightService() {
        this(context -> { throw new ResourceConflictException(UNAVAILABLE); }, java.time.Clock.systemUTC());
    }

    AliExpressFreightService(AliExpressFreightGateway gateway, java.time.Clock clock) {
        this.gateway = java.util.Objects.requireNonNull(gateway);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    // Internal candidate only. Not exposed as a checkout selection until persisted
    // ownership, expiry, snapshot and provider contract verification are implemented.
    AliExpressFreightQuote candidate(AliExpressFreightQuote.Context context) {
        try {
            return new AliExpressFreightQuote(context, gateway.calculate(context), clock.instant());
        } catch (RuntimeException exception) {
            // Do not propagate provider messages, causes, payloads or destination.
            throw new ResourceConflictException(UNAVAILABLE);
        }
    }

    public List<ShippingProvider.Option> quote(ShippingProvider.Request request) {
        throw new ResourceConflictException(UNAVAILABLE);
    }

    /** Covers unpaid legacy orders too; existing paid orders are never modified. */
    public static void requireConfirmedFreight(Order order) {
        if (order.getItems().stream().anyMatch(item -> "ALIEXPRESS".equalsIgnoreCase(item.getSupplier())
                && item.getFulfillmentType() != com.garage.garageapi.product.entity.FulfillmentType.LOCAL_STOCK)) {
            throw new ResourceConflictException(UNAVAILABLE);
        }
    }
}

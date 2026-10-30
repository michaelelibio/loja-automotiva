package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Fail closed until the application's freight contract supports a confirmed
 * destination and SKU. Current freight APIs document SKU selection, but not
 * postal-code/full-address pricing. App grants and BR destination coverage
 * remain unverified; see ALIEXPRESS-CHECKOUT-FREIGHT-AUDIT.md.
 * Never substitute local shipping or treat a country-level estimate as a quote.
 */
@Service
public class AliExpressFreightService {
    public static final String UNAVAILABLE =
            "Aguarde a cotação confirmada, aceite o total e pague dentro da validade.";
    private final AliExpressFreightGateway gateway;

    public AliExpressFreightService(AliExpressFreightGateway gateway) {
        this.gateway=java.util.Objects.requireNonNull(gateway);
    }

    public List<ShippingProvider.Option> quote(ShippingProvider.Request request) {
        final AliExpressFreightGateway.Evaluation evaluation;
        try { evaluation=gateway.calculate(request); }
        catch (AliExpressShippingException ex) { throw ex; }
        catch (RuntimeException ignored) { throw new AliExpressShippingException(AliExpressShippingException.Code.EXTERNAL_FAILURE); }
        if (evaluation == null || evaluation.coverage() == null || evaluation.lines().isEmpty()
                || evaluation.lines().stream().anyMatch(line -> line == null || line.methods().isEmpty()))
            throw new AliExpressShippingException(AliExpressShippingException.Code.FREIGHT_UNAVAILABLE);
        // A real call and normalized rates do not prove address coverage or reserve a price.
        // There is deliberately no address-confirmed branch or payment-enabling flag yet.
        switch(evaluation.coverage()) {
            case COUNTRY_ONLY -> throw new AliExpressShippingException(AliExpressShippingException.Code.DESTINATION_NOT_CONFIRMED);
        }
        throw new AliExpressShippingException(AliExpressShippingException.Code.INVALID_RESPONSE);
    }

    /** Covers unpaid legacy orders too; existing paid orders are never modified. */
    public static void requireConfirmedFreight(Order order) {
        if (order.getItems().stream().anyMatch(item -> "ALIEXPRESS".equalsIgnoreCase(item.getSupplier())
                && item.getFulfillmentType() != com.garage.garageapi.product.entity.FulfillmentType.LOCAL_STOCK)) {
            var quote = order.getAssistedQuote();
            if (quote == null || !"ACCEPTED".equals(quote.state()) || quote.acceptedAt() == null
                    || !java.time.Instant.now().isBefore(quote.validUntil()) || order.getShippingLegs().isEmpty())
                throw new ResourceConflictException(UNAVAILABLE);
            order.requireQuoteContextUnchanged();
        }
    }
}

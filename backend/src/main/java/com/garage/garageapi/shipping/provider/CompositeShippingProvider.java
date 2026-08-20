package com.garage.garageapi.shipping.provider;

import com.garage.garageapi.product.entity.FulfillmentType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Primary
@Component
public class CompositeShippingProvider implements ShippingProvider {
    private final FixedShippingProvider localProvider;
    private final CjShippingProvider cjProvider;

    public CompositeShippingProvider(FixedShippingProvider localProvider, CjShippingProvider cjProvider) {
        this.localProvider = localProvider;
        this.cjProvider = cjProvider;
    }

    @Override
    public List<Option> quote(Request request) {
        List<Item> local = request.items().stream()
                .filter(item -> item.fulfillmentType() == FulfillmentType.LOCAL_STOCK
                        || (item.fulfillmentType() == FulfillmentType.DROPSHIPPING
                        && !"CJ".equalsIgnoreCase(item.supplier())))
                .toList();
        List<Item> cj = request.items().stream()
                .filter(item -> item.fulfillmentType() == FulfillmentType.DROPSHIPPING
                        && "CJ".equalsIgnoreCase(item.supplier())).toList();
        List<Option> localOptions = local.isEmpty() ? List.of()
                : localProvider.quote(new Request(request.zipCode(), local));
        List<Option> cjOptions = cj.isEmpty() ? List.of() : cjProvider.quote(request.zipCode(), cj);
        if (local.isEmpty()) return cjOptions;
        if (cj.isEmpty()) return localOptions;

        Option localOption = localOptions.get(0);
        List<Option> combined = new ArrayList<>();
        for (Option cjOption : cjOptions) {
            var price = localOption.price().add(cjOption.price()).setScale(2, RoundingMode.HALF_UP);
            List<Leg> legs = new ArrayList<>(localOption.legs());
            legs.addAll(cjOption.legs());
            combined.add(new Option("MIX-" + cjOption.code().substring(3),
                    "Entrega local + " + cjOption.name(), price,
                    Math.max(localOption.estimatedDays(), cjOption.estimatedDays()),
                    "COMPOSITE", price, "BRL", List.copyOf(legs)));
        }
        return List.copyOf(combined);
    }
}

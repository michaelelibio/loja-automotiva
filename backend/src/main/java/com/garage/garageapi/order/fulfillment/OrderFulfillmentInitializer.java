package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.Locale;

@Service
public class OrderFulfillmentInitializer {
    private final OrderFulfillmentRepository repository;

    public OrderFulfillmentInitializer(OrderFulfillmentRepository repository) {
        this.repository = repository;
    }

    public void initialize(Order order) {
        Set<String> providers = order.getItems().stream()
                .filter(item -> item.getFulfillmentType() == FulfillmentType.DROPSHIPPING)
                .map(item -> item.getSupplier() == null ? "" : item.getSupplier().trim().toUpperCase(Locale.ROOT))
                .filter(provider -> "CJ".equals(provider) || "DSERS".equals(provider))
                .collect(java.util.stream.Collectors.toSet());
        String provider = providers.isEmpty() ? "NONE"
                : providers.size() == 1 ? providers.iterator().next() : "MULTIPLE";
        repository.save(new OrderFulfillment(order, provider));
    }
}

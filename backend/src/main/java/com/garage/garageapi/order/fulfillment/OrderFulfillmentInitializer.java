package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import org.springframework.stereotype.Service;

@Service
public class OrderFulfillmentInitializer {
    private final OrderFulfillmentRepository repository;

    public OrderFulfillmentInitializer(OrderFulfillmentRepository repository) {
        this.repository = repository;
    }

    public void initialize(Order order) {
        boolean requiresCj = order.getItems().stream().anyMatch(item ->
                item.getFulfillmentType() == FulfillmentType.DROPSHIPPING
                        && "CJ".equalsIgnoreCase(item.getSupplier()));
        repository.save(new OrderFulfillment(order, requiresCj));
    }
}

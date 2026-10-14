package com.garage.garageapi.order.fulfillment;

import org.springframework.stereotype.Service;

@Service
public class SupplierFulfillmentService {
    private final OrderFulfillmentRepository repository;
    private final CjFulfillmentService cjService;
    private final DsersFulfillmentService dsersService;

    public SupplierFulfillmentService(OrderFulfillmentRepository repository,
                                      CjFulfillmentService cjService,
                                      DsersFulfillmentService dsersService) {
        this.repository = repository;
        this.cjService = cjService;
        this.dsersService = dsersService;
    }

    public void fulfill(Long orderId) {
        repository.findByOrderId(orderId).ifPresent(fulfillment -> {
            if ("CJ".equals(fulfillment.getProvider())) cjService.fulfill(orderId);
            else if ("DSERS".equals(fulfillment.getProvider())) dsersService.fulfill(orderId);
        });
    }
}

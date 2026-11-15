package com.garage.garageapi.order.fulfillment;

import org.springframework.stereotype.Service;

@Service
public class SupplierFulfillmentService {
    private final OrderFulfillmentRepository repository;
    private final CjFulfillmentService cjService;
    private final DsersFulfillmentService dsersService;
    private final org.springframework.beans.factory.ObjectProvider<com.garage.garageapi.integration.aliexpress.AliExpressDispatchWorker> aliExpress;

    public SupplierFulfillmentService(OrderFulfillmentRepository repository,
                                      CjFulfillmentService cjService,
                                      DsersFulfillmentService dsersService,
                                      org.springframework.beans.factory.ObjectProvider<com.garage.garageapi.integration.aliexpress.AliExpressDispatchWorker> aliExpress) {
        this.repository = repository;
        this.cjService = cjService;
        this.dsersService = dsersService;
        this.aliExpress = aliExpress;
    }

    public void fulfill(Long orderId) {
        aliExpress.ifAvailable(worker->worker.enqueue(orderId));
        repository.findByOrderId(orderId).ifPresent(fulfillment -> {
            if ("CJ".equals(fulfillment.getProvider())) cjService.fulfill(orderId);
            else if ("DSERS".equals(fulfillment.getProvider())) dsersService.fulfill(orderId);
        });
    }
}

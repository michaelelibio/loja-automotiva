package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.task.TaskExecutor;
import java.time.Instant;

/** The committed paid fulfillment is the durable queue; executor callbacks are just a fast path. */
public class AliExpressDispatchWorker {
    private static final Logger log=LoggerFactory.getLogger(AliExpressDispatchWorker.class);
    private final OrderFulfillmentRepository repository;
    private final AliExpressOrderService service;
    private final TaskExecutor executor;
    private final AliExpressAutomationPolicy automation;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private AliExpressDispatchRecordRepository secondary;
    public AliExpressDispatchWorker(OrderFulfillmentRepository repository,AliExpressOrderService service,TaskExecutor executor,
            AliExpressAutomationPolicy automation) {
        this.repository=repository;this.service=service;this.executor=executor;this.automation=automation;
    }
    public void enqueue(Long orderId) {
        if(!automation.enabled())return;
        try {executor.execute(()->{
            try {service.sendAutomatically(orderId);}
            catch(RuntimeException ignored){log.warn("AliExpress dispatch deferred; orderId={}; recovery=scheduled",orderId);}
        });}catch(RuntimeException ignored){log.warn("AliExpress dispatch queue full; orderId={}; recovery=scheduled",orderId);}
    }
    @Scheduled(fixedDelayString="${aliexpress.orders.dispatch-poll-ms:60000}",initialDelayString="${aliexpress.orders.dispatch-poll-ms:60000}")
    public void recoverPending() {
        if(!automation.enabled())return;
        Instant now=Instant.now(),createdFrom=automation.ordersCreatedFrom();
        for(Long id:repository.findAutomaticDispatchCandidates(now,createdFrom,PageRequest.of(0,20)))enqueue(id);
        if(secondary!=null)for(Long id:secondary.recoveryCandidates(now,createdFrom,PageRequest.of(0,20)))enqueue(id);
    }
}

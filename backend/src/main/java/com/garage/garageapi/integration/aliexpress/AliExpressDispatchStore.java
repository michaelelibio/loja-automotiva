package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.fulfillment.*;
import org.springframework.stereotype.Component;

@Component
public class AliExpressDispatchStore {
    private final OrderFulfillmentRepository legacy;
    private final AliExpressDispatchRecordRepository secondary;
    public AliExpressDispatchStore(OrderFulfillmentRepository legacy,AliExpressDispatchRecordRepository secondary) {this.legacy=legacy;this.secondary=secondary;}
    public OrderFulfillment find(Long id) {
        var extra=secondary.findById(id).orElse(null);
        if(extra!=null)return extra.restore();
        return legacy.findByOrderId(id).filter(f->"ALIEXPRESS".equals(f.getProvider())).orElse(null);
    }
    /** Caller holds the common order lifecycle lock throughout this transaction. */
    public OrderFulfillment locked(Order order) {
        var extra=secondary.findById(order.getId()).orElse(null);
        if(extra!=null)return extra.restore();
        var f=legacy.findByOrderIdForUpdate(order.getId()).orElse(null);
        if(f!=null && "ALIEXPRESS".equals(f.getProvider()))return f;
        return secondary.saveAndFlush(new AliExpressDispatchRecord(order)).restore();
    }
    public void flush(OrderFulfillment f) {
        if(f.getId()!=null) {legacy.flush();return;}
        var extra=secondary.findById(f.getOrder().getId()).orElseThrow();extra.update(f);secondary.flush();
    }
}

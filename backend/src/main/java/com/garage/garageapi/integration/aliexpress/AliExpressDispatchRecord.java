package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.fulfillment.*;
import jakarta.persistence.*;
import java.time.Instant;

/** Separate supplier stream for mixed orders. Never overwrites CJ, DSers or local fulfillment. */
@Entity @Table(name="aliexpress_dispatch_records")
public class AliExpressDispatchRecord implements org.springframework.data.domain.Persistable<Long> {
    @Id @Column(name="order_id") private Long orderId;
    @OneToOne(fetch=FetchType.LAZY) @MapsId @JoinColumn(name="order_id")
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Order order;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private FulfillmentStatus status;
    @Column(name="next_check_at") private Instant nextCheckAt;
    @Column(name="automatic_eligible",nullable=false) private boolean automaticEligible;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable=false,columnDefinition="jsonb") private OrderFulfillment.AliExpressState state;
    protected AliExpressDispatchRecord() { }
    @Transient private boolean newRecord=true;
    @Override public Long getId() {return orderId;}
    @Override public boolean isNew() {return newRecord;}
    @PostLoad @PostPersist void persisted() {newRecord=false;}
    public AliExpressDispatchRecord(Order order) {this.order=order;this.orderId=order.getId();update(OrderFulfillment.restoreAliExpress(order,null));}
    public OrderFulfillment restore() {return OrderFulfillment.restoreAliExpress(order,state);}
    public void update(OrderFulfillment f) {state=f.aliExpressState();status=f.getStatus();nextCheckAt=f.getDispatchNextCheckAt();
        automaticEligible=!f.isDispatchManualHold() && status==FulfillmentStatus.PENDING && f.getAttemptCount()==0 && (f.getLastError()==null || nextCheckAt!=null);}
}

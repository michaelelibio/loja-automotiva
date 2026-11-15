package com.garage.garageapi.payment.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Durable scheduling metadata only. Never stores provider payloads, credentials or payer data. */
@Entity
@Table(name="payment_reconciliations")
public class PaymentReconciliation {
    @Id @Column(name="payment_id") private Long paymentId;
    @Column(name="lease_token",length=36) private String leaseToken;
    @Column(name="next_check_at",nullable=false) private Instant nextCheckAt;
    @Column(name="last_checked_at") private Instant lastCheckedAt;
    @Column(name="failure_count",nullable=false) private int failureCount;
    @Column(name="last_failure_code",length=40) private String lastFailureCode;
    @Column(name="review_required",nullable=false) private boolean reviewRequired;
    protected PaymentReconciliation() { }
    PaymentReconciliation(Long paymentId,Instant now) { this.paymentId=paymentId;nextCheckAt=now; }
    String claim(Instant now) {
        if(reviewRequired || nextCheckAt.isAfter(now))return null;
        leaseToken=UUID.randomUUID().toString();nextCheckAt=now.plusSeconds(300);return leaseToken;
    }
    boolean owns(String token) { return token!=null && token.equals(leaseToken); }
    void complete(String token,Instant now,long intervalSeconds,String failureCode,boolean manualReview) {
        if(!owns(token))return;
        leaseToken=null;lastCheckedAt=now;lastFailureCode=failureCode;reviewRequired=manualReview;
        failureCount=failureCode==null?0:Math.min(failureCount+1,30);
        long delay=failureCode==null?intervalSeconds:Math.min(3600,30L*(1L<<Math.min(failureCount,7)));
        nextCheckAt=now.plusSeconds(delay);
    }
    public Long getPaymentId(){return paymentId;}
    public Instant getNextCheckAt(){return nextCheckAt;}
    public Instant getLastCheckedAt(){return lastCheckedAt;}
    public int getFailureCount(){return failureCount;}
    public String getLastFailureCode(){return lastFailureCode;}
    public boolean isReviewRequired(){return reviewRequired;}
}

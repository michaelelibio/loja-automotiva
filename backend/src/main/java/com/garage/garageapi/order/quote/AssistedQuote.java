package com.garage.garageapi.order.quote;

import java.math.BigDecimal;
import java.time.Instant;

/** Bound to the immutable address, cart and logistics snapshots of its order. */
public record AssistedQuote(String state, int revision, Instant confirmedAt, Instant validUntil,
        Instant acceptedAt, BigDecimal supplierTotalBrl, String evidenceReference, String confirmedBy,
        Instant dispatchCheckedAt, String resolutionReference, Instant lastCheckedAt,
        BigDecimal observedSupplierTotalBrl, String lastCheckedBy, String holdReason, String contextDigest) {
    public AssistedQuote(String state,int revision,Instant confirmedAt,Instant validUntil,Instant acceptedAt,
            BigDecimal supplierTotalBrl,String evidenceReference,String confirmedBy,Instant dispatchCheckedAt,String resolutionReference) {
        this(state,revision,confirmedAt,validUntil,acceptedAt,supplierTotalBrl,evidenceReference,confirmedBy,
                dispatchCheckedAt,resolutionReference,null,null,null,null,null);
    }
    @Override public String toString() { return "AssistedQuote[state="+state+",revision="+revision+"]"; }
    public static AssistedQuote waiting() {
        return new AssistedQuote("WAITING",0,null,null,null,null,null,null,null,null);
    }
    public AssistedQuote accept() {
        return new AssistedQuote("ACCEPTED",revision,confirmedAt,validUntil,Instant.now(),supplierTotalBrl,
                evidenceReference,confirmedBy,null,null,lastCheckedAt,observedSupplierTotalBrl,lastCheckedBy,null,contextDigest);
    }
    public AssistedQuote checked(boolean unchanged) {
        return new AssistedQuote(unchanged ? state : "REFUND_REQUIRED",revision,confirmedAt,validUntil,
                acceptedAt,supplierTotalBrl,evidenceReference,confirmedBy,unchanged ? Instant.now() : null,null,
                lastCheckedAt,observedSupplierTotalBrl,lastCheckedBy,unchanged ? null : "PAYMENT_OUTSIDE_CONFIRMED_QUOTE",contextDigest);
    }
    public AssistedQuote rechecked(BigDecimal observed,boolean unchanged,String admin) {
        Instant now=Instant.now();
        return new AssistedQuote(unchanged ? state : "REFUND_REQUIRED",revision,confirmedAt,validUntil,
                acceptedAt,supplierTotalBrl,evidenceReference,confirmedBy,unchanged ? now : null,null,
                now,observed,admin,unchanged ? null : "COST_OR_DELIVERY_CHANGED",contextDigest);
    }
    public AssistedQuote refunded(String reference) {
        return new AssistedQuote("REFUNDED",revision,confirmedAt,validUntil,acceptedAt,supplierTotalBrl,
                evidenceReference,confirmedBy,null,reference,lastCheckedAt,observedSupplierTotalBrl,lastCheckedBy,holdReason,contextDigest);
    }
    public AssistedQuote withContextDigest(String digest) {
        return new AssistedQuote(state,revision,confirmedAt,validUntil,acceptedAt,supplierTotalBrl,evidenceReference,
                confirmedBy,dispatchCheckedAt,resolutionReference,lastCheckedAt,observedSupplierTotalBrl,lastCheckedBy,holdReason,digest);
    }
    public Summary summary() { return new Summary(state,revision,validUntil,acceptedAt); }
    public record Summary(String state,int revision,Instant validUntil,Instant acceptedAt) {}
}

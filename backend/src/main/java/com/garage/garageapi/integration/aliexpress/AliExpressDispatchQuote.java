package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Supplier costs, including explicit zero taxes/freight, bound to one immutable order context. */
public record AliExpressDispatchQuote(String contextDigest, String buyerHash, String appHash,
        List<Line> lines, BigDecimal productTotal, BigDecimal shippingTotal, BigDecimal taxTotal,
        BigDecimal total, Instant verifiedAt, Instant validUntil, String verifiedBy, String evidenceReference) {
    public record Line(String productId, String skuId, String skuAttr, int quantity,
                       BigDecimal unitPrice, String logisticsService) { }
    @Override public String toString() { return "AliExpressDispatchQuote[REDACTED]"; }
}

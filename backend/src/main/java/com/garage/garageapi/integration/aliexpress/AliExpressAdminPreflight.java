package com.garage.garageapi.integration.aliexpress;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
public record AliExpressAdminPreflight(String id,String actor,String fixtureDigest,String orderSnapshotDigest,
        String tokenSha256,String buyerUserId,String buyerAccount,String buyerState,Instant checkedAt,Instant validUntil,
        AliExpressTestFixture fixture,List<String> warnings,String productCostSource,BigDecimal inGarageProductTotal) {
    @Override public String toString(){return "AliExpressAdminPreflight[REDACTED]";}
}

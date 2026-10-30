package com.garage.garageapi.shipping.commercial;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record CommercialShippingSnapshot(Long tableVersion,String decisionCode,String state,Instant validUntil,
        BigDecimal supplierCostBrl,BigDecimal reserveBrl,BigDecimal paymentFeesBrl,BigDecimal netProfitBrl,
        List<String> rateKeys,List<String> evidenceReferences,String contextDigest) {
    public CommercialShippingSnapshot {rateKeys=List.copyOf(rateKeys);evidenceReferences=List.copyOf(evidenceReferences);}
    public CommercialShippingSnapshot bind(String digest){return new CommercialShippingSnapshot(tableVersion,decisionCode,state,validUntil,supplierCostBrl,reserveBrl,paymentFeesBrl,netProfitBrl,rateKeys,evidenceReferences,digest);}
}

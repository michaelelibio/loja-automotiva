package com.garage.garageapi.shipping.commercial;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record CommercialShippingSnapshot(Long tableVersion,String decisionCode,String state,Instant validUntil,
        BigDecimal supplierCostBrl,BigDecimal reserveBrl,BigDecimal paymentFeesBrl,BigDecimal netProfitBrl,
        List<String> rateKeys,List<String> evidenceReferences,String contextDigest,String policyType) {
    public CommercialShippingSnapshot {rateKeys=List.copyOf(rateKeys);evidenceReferences=List.copyOf(evidenceReferences);}
    public CommercialShippingSnapshot(Long version,String code,String state,Instant until,BigDecimal cost,BigDecimal reserve,BigDecimal fees,BigDecimal profit,List<String> keys,List<String> refs,String digest){this(version,code,state,until,cost,reserve,fees,profit,keys,refs,digest,null);}
    public CommercialShippingSnapshot bind(String digest){return new CommercialShippingSnapshot(tableVersion,decisionCode,state,validUntil,supplierCostBrl,reserveBrl,paymentFeesBrl,netProfitBrl,rateKeys,evidenceReferences,digest,policyType);}
}

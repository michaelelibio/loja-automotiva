package com.garage.garageapi.integration.aliexpress;

import java.util.List;
import static com.garage.garageapi.integration.aliexpress.AliExpressDsFreightClient.Observation;

/** Equality concerns sanitized observations only, never the complete supplier response or causation. */
public record AliExpressFreightProvinceComparison(String executionStatus, String safeCode,
        Observation countryOnly, Observation withProvince, String responseComparison, List<String> limitations) {
    public AliExpressFreightProvinceComparison { limitations=List.copyOf(limitations); }
    static AliExpressFreightProvinceComparison of(String execution,String code,Observation country,Observation province) {
        String comparison="INSUFFICIENT_OBSERVABLE_DATA";
        if (country!=null && province!=null && observable(country) && observable(province)) {
            var a=country.result();var b=province.result();
            boolean equal=a.status()==b.status() && a.safeCode().equals(b.safeCode())
                    && a.methods().equals(b.methods()) && a.monetaryStructure().equals(b.monetaryStructure());
            comparison=equal?"NO_OBSERVED_DIFFERENCE":"OBSERVED_DIFFERENCE";
        }
        return new AliExpressFreightProvinceComparison(execution,code,country,province,comparison,List.of(
                "SANITIZED_PROJECTION_COMPARISON_ONLY","PROVINCE_EFFECT_NOT_CONFIRMED",
                "SEQUENTIAL_CALLS_NOT_CAUSAL_PROOF","PROVINCE_DOCUMENTED_FOR_LOCAL_TO_LOCAL",
                "NO_PROVINCE_CODE_OR_CITY_CODE","NO_POSTAL_CODE_QUOTE","DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED"));
    }
    private static boolean observable(Observation observation) {
        var result=observation.result();
        return observation.request().httpResponseReceived() && (result.status()==AliExpressFreightDiagnosticResult.Status.SUCCESS
                || !result.monetaryStructure().isEmpty() || result.safeCode().equals("NO_DELIVERY_METHODS"));
    }
}

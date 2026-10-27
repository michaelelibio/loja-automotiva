package com.garage.garageapi.integration.aliexpress;

import java.util.List;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status;

/** Observed public territorial labels/identifiers, never a shipping quote or an address. */
public record AliExpressTerritorialDiagnosticResult(Status status, String safeCode, String applicationAccess,
        String country, List<Match> matches, List<String> observedFieldNames, List<String> limitations,
        String providerCode, String providerSubCode) {
    public record Scalar(String field, String jsonType, String value) {}
    public record Node(List<Scalar> fields) { public Node { fields=List.copyOf(fields); } }
    public record Match(String target, int depth, List<Node> ancestors, Node observed) {
        public Match { ancestors=List.copyOf(ancestors); }
    }
    public AliExpressTerritorialDiagnosticResult {
        matches=List.copyOf(matches); observedFieldNames=List.copyOf(observedFieldNames); limitations=List.copyOf(limitations);
    }
    static AliExpressTerritorialDiagnosticResult of(Status status,String code,boolean accepted,String country,
            List<Match> matches,List<String> fieldNames) {
        return new AliExpressTerritorialDiagnosticResult(status,code,accepted?"CALL_ACCEPTED":"NOT_CONFIRMED",country,matches,fieldNames,
                List.of("DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED","TERRITORIAL_IDS_NOT_FREIGHT_MAPPING",
                        "POSTAL_CODE_SUPPORT_NOT_CONFIRMED","TARGET_NOT_OBSERVED_DOES_NOT_PROVE_UNAVAILABLE"),null,null);
    }
    AliExpressTerritorialDiagnosticResult withProviderCodes(String code,String subCode) {
        return new AliExpressTerritorialDiagnosticResult(status,safeCode,applicationAccess,country,matches,observedFieldNames,limitations,code,subCode);
    }
    static AliExpressTerritorialDiagnosticResult failure(Status status,String code) {
        return of(status,code,false,null,List.of(),List.of());
    }
}

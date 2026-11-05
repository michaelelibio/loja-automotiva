package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Component;
import java.util.List;

/** Fail closed: local booleans, an existing token and a simulated payment are not provider proof. */
@Component
public class AliExpressTestReadiness {
    private final AliExpressPlaceOrderPermission permission;
    private final AliExpressAuthorizationDiagnostics authorization;
    public AliExpressTestReadiness(AliExpressPlaceOrderPermission permission, AliExpressAuthorizationDiagnostics authorization) {
        this.permission=permission; this.authorization=authorization;
    }
    public List<String> blockers(AliExpressTestFixture fixture) {
        var reasons=new java.util.ArrayList<String>();
        if (!permission.verified()) reasons.add("Permissão placeorder não comprovada.");
        if (!"LOCALLY_USABLE".equals(authorization.inspect().accessTokenState())) reasons.add("OAuth local indisponível ou expirado.");
        // No homologated, read-only verifier currently proves these facts. Do not replace with operator flags.
        reasons.add("Faltam evidências homologadas de OAuth no fornecedor, assinatura e gateway placeorder.");
        reasons.add("Faltam validação atual do produto/SKU, entrega e reconferência de valores, impostos e custos da configuração de teste.");
        return List.copyOf(reasons);
    }
}

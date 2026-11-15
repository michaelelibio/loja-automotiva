package com.garage.garageapi.integration.aliexpress;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@Profile("!local-freight | controlled | production | prod")
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/integrations/aliexpress")
public class AliExpressOrderAuthorizationController {
    private final AliExpressAuthorizationDiagnostics diagnostics;
    private final AliExpressPlaceOrderPermission permission;
    public AliExpressOrderAuthorizationController(AliExpressAuthorizationDiagnostics diagnostics, AliExpressPlaceOrderPermission permission) {
        this.diagnostics=diagnostics; this.permission=permission;
    }
    @GetMapping("/authorization-diagnostic")
    public ResponseEntity<AliExpressAuthorizationDiagnostics.Report> inspect() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(diagnostics.inspect());
    }
    public record PlaceOrderStatus(String method, String authorization, boolean realCreationAllowed) { }
    @GetMapping("/placeorder-authorization")
    public ResponseEntity<PlaceOrderStatus> placeOrder() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PlaceOrderStatus(
                "aliexpress.trade.buy.placeorder",permission.status(),permission.verified()));
    }
}

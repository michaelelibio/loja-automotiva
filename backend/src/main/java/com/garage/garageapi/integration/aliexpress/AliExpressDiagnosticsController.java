package com.garage.garageapi.integration.aliexpress;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/admin/integrations/aliexpress")
@PreAuthorize("hasRole('ADMIN')")
public class AliExpressDiagnosticsController {
    private final AliExpressAuthorizationDiagnostics diagnostics;
    private final AliExpressFreightDiagnosticService freight;
    public AliExpressDiagnosticsController(AliExpressAuthorizationDiagnostics diagnostics, AliExpressFreightDiagnosticService freight) {
        this.diagnostics = diagnostics; this.freight=freight;
    }

    @GetMapping("/authorization-diagnostic")
    public ResponseEntity<AliExpressAuthorizationDiagnostics.Report> inspect() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(diagnostics.inspect());
    }
    @PostMapping("/freight-diagnostic/product")
    public ResponseEntity<AliExpressFreightDiagnosticService.Preparation> freightProduct(@RequestBody AliExpressFreightDiagnosticRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(freight.prepare(request.productId()));
    }
    @PostMapping("/freight-diagnostic")
    public ResponseEntity<AliExpressFreightDiagnosticResult> freight(@RequestBody(required=false) AliExpressFreightDiagnosticRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(freight.run(request));
    }
}

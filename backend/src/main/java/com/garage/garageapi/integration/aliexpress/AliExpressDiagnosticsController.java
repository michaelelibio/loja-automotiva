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
    public AliExpressDiagnosticsController(AliExpressAuthorizationDiagnostics diagnostics) { this.diagnostics = diagnostics; }

    @GetMapping("/authorization-diagnostic")
    public ResponseEntity<AliExpressAuthorizationDiagnostics.Report> inspect() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(diagnostics.inspect());
    }
}

package com.garage.garageapi.integration.aliexpress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AliExpressCallbackController {
    private static final Logger logger = LoggerFactory.getLogger(AliExpressCallbackController.class);
    private final AliExpressAuthService authService;

    public AliExpressCallbackController(AliExpressAuthService authService) {
        this.authService = authService;
    }

    @GetMapping(value = "/api/integrations/aliexpress/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        // OAuth values and provider error descriptions may contain sensitive data.
        // Log only presence and length, never the raw query parameters.
        logger.info("AliExpress callback received; code={}; state={}; error={}; error_description={}",
                diagnostic(code), diagnostic(state), diagnostic(error), diagnostic(errorDescription));
        if (error != null) {
            return ResponseEntity.badRequest().body("Autorizacao AliExpress recusada ou interrompida. Tente novamente.");
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Callback AliExpress incompleto: codigo de autorizacao ausente.");
        }
        try {
            authService.exchangeCode(code);
            return ResponseEntity.ok("Autorizacao AliExpress concluida com sucesso.");
        } catch (AliExpressAuthException exception) {
            return ResponseEntity.status(502).body(exception.getMessage());
        }
    }

    private static String diagnostic(String value) {
        return value == null ? "absent" : "present(length=" + value.length() + ")";
    }
}

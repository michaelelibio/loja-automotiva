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
    private final AliExpressOAuthStateService states;
    private final AliExpressTokenService tokens;

    public AliExpressCallbackController(AliExpressAuthService authService, AliExpressOAuthStateService states,
                                       AliExpressTokenService tokens) {
        this.authService = authService;
        this.states = states;
        this.tokens = tokens;
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
            if (state != null) states.consume(state);
            AliExpressTokenData data = authService.exchangeCode(code);
            // Legacy callbacks keep their response but cannot replace persisted authorization.
            if (state != null) tokens.store(data);
            return ResponseEntity.ok("Autorizacao AliExpress concluida com sucesso.");
        } catch (AliExpressAuthException exception) {
            return ResponseEntity.status(502).body(exception.getMessage());
        } catch (AliExpressIntegrationException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    private static String diagnostic(String value) {
        return value == null ? "absent" : "present(length=" + value.length() + ")";
    }
}

package com.garage.garageapi.integration.aliexpress;

public class AliExpressAuthException extends RuntimeException {
    public AliExpressAuthException() {
        // Do not attach upstream response bodies, request values or exception causes.
        super("Nao foi possivel concluir a autorizacao AliExpress. Tente autorizar novamente.");
    }
}

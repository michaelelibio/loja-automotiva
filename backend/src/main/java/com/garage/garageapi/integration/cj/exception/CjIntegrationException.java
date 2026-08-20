package com.garage.garageapi.integration.cj.exception;

public class CjIntegrationException extends RuntimeException {
    private final Reason reason;

    public CjIntegrationException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public CjIntegrationException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }

    public enum Reason {
        NOT_CONFIGURED, AUTHENTICATION, RATE_LIMIT, UPSTREAM, INVALID_RESPONSE, CONFLICT
    }
}

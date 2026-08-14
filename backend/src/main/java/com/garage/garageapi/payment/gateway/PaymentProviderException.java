package com.garage.garageapi.payment.gateway;

public class PaymentProviderException extends RuntimeException {
    public enum Reason {
        GENERIC,
        DEFINITIVE_REJECTION
    }

    private final Reason reason;

    public PaymentProviderException(String message) {
        this(message, null, Reason.GENERIC);
    }

    public PaymentProviderException(String message, Throwable cause) {
        this(message, cause, Reason.GENERIC);
    }

    public PaymentProviderException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public boolean isDefinitiveRejection() {
        return reason == Reason.DEFINITIVE_REJECTION;
    }
}

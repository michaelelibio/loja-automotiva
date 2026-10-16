package com.garage.garageapi.order.dto;

/** Never include personal values in binding diagnostics or request logs. */
public record AliExpressRecipientRequest(String cpf, String birthDate, String phone) {
    @Override public String toString() { return "AliExpressRecipientRequest[REDACTED]"; }
}

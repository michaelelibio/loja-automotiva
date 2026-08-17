package com.garage.garageapi.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull(message = "método de pagamento é obrigatório") Method method
) {
    public enum Method { MERCADO_PAGO }
}

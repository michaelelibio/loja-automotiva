package com.garage.garageapi.payment.dto;

import com.garage.garageapi.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull(message = "método de pagamento é obrigatório") PaymentMethod method
) { }

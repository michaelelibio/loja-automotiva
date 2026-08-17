package com.garage.garageapi.shipping.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShippingQuoteItemRequest(
        @NotNull(message = "produto é obrigatório") Long productId,
        @NotNull(message = "quantidade é obrigatória")
        @Positive(message = "quantidade deve ser maior que zero") Integer quantity
) { }

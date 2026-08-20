package com.garage.garageapi.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotNull(message = "produto é obrigatório") Long productId,
        Long variantId,
        @NotNull(message = "quantidade é obrigatória")
        @Positive(message = "quantidade deve ser maior que zero") Integer quantity
) { }

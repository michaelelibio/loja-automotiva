package com.garage.garageapi.admin.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminStockMovementRequest(
        @NotNull @Positive Long productId,
        @NotNull AdminStockMovementType type,
        @Positive int quantity,
        @NotBlank @Size(max = 500) String reason
) { }

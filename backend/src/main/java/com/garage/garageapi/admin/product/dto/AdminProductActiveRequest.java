package com.garage.garageapi.admin.product.dto;

import jakarta.validation.constraints.NotNull;

public record AdminProductActiveRequest(@NotNull Boolean active) { }

package com.garage.garageapi.admin.order.dto;

import com.garage.garageapi.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusUpdateRequest(
        @NotNull(message = "Status é obrigatório") OrderStatus status
) { }

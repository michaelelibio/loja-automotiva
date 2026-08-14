package com.garage.garageapi.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "endereço é obrigatório") Long addressId,
        @NotEmpty(message = "pedido deve possuir ao menos um item")
        List<@Valid CreateOrderItemRequest> items
) { }

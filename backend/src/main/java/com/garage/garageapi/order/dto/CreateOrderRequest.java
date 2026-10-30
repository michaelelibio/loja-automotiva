package com.garage.garageapi.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "endereço é obrigatório") Long addressId,
        @NotEmpty(message = "pedido deve possuir ao menos um item")
        List<@Valid CreateOrderItemRequest> items,
        @Size(max = 50, message = "código de frete inválido") String shippingCode,
        AliExpressRecipientRequest recipient,
        @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") String requestKey
) {
    public CreateOrderRequest(Long addressId,List<CreateOrderItemRequest> items,String shippingCode) {
        this(addressId,items,shippingCode,null,null);
    }
    public CreateOrderRequest(Long addressId,List<CreateOrderItemRequest> items,String shippingCode,AliExpressRecipientRequest recipient) {
        this(addressId,items,shippingCode,recipient,null);
    }
    @Override public String toString() { return "CreateOrderRequest[recipient=REDACTED]"; }
}

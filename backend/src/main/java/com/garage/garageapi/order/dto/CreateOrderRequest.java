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
        @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") String requestKey,
        @jakarta.validation.constraints.DecimalMin("0.01") java.math.BigDecimal expectedTotal,
        AddressConsent expectedAddress
) {
    public record AddressConsent(String recipientName,String zipCode,String street,String number,String complement,String neighborhood,String city,String state){
        public boolean matches(com.garage.garageapi.address.entity.Address a){
            return java.util.Objects.equals(recipientName,a.getRecipientName())&&java.util.Objects.equals(zipCode,a.getZipCode())
                &&java.util.Objects.equals(street,a.getStreet())&&java.util.Objects.equals(number,a.getNumber())
                &&java.util.Objects.equals(complement,a.getComplement())&&java.util.Objects.equals(neighborhood,a.getNeighborhood())
                &&java.util.Objects.equals(city,a.getCity())&&java.util.Objects.equals(state,a.getState());
        }
    }
    public CreateOrderRequest(Long addressId,List<CreateOrderItemRequest> items,String shippingCode,AliExpressRecipientRequest recipient,String requestKey){
        this(addressId,items,shippingCode,recipient,requestKey,null,null);
    }
    public CreateOrderRequest(Long addressId,List<CreateOrderItemRequest> items,String shippingCode) {
        this(addressId,items,shippingCode,null,null);
    }
    public CreateOrderRequest(Long addressId,List<CreateOrderItemRequest> items,String shippingCode,AliExpressRecipientRequest recipient) {
        this(addressId,items,shippingCode,recipient,null);
    }
    @Override public String toString() { return "CreateOrderRequest[recipient=REDACTED]"; }
}

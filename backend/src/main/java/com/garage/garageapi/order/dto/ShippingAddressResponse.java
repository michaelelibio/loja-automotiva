package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.Order;

public record ShippingAddressResponse(String recipientName, String zipCode, String street,
                                      String number, String complement, String neighborhood,
                                      String city, String state) {
    public static ShippingAddressResponse from(Order order) {
        return new ShippingAddressResponse(order.getRecipientName(), order.getZipCode(),
                order.getStreet(), order.getNumber(), order.getComplement(), order.getNeighborhood(),
                order.getCity(), order.getState());
    }
}

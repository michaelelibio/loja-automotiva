package com.garage.garageapi.address.dto;

import com.garage.garageapi.address.entity.Address;

import java.time.Instant;

public record AddressResponse(Long id, String label, String recipientName, String zipCode,
                              String street, String number, String complement, String neighborhood,
                              String city, String state, boolean isPrimary, Instant createdAt,
                              Instant updatedAt) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                address.getZipCode(), address.getStreet(), address.getNumber(), address.getComplement(),
                address.getNeighborhood(), address.getCity(), address.getState(), address.isPrimary(),
                address.getCreatedAt(), address.getUpdatedAt());
    }
}

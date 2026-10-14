package com.garage.garageapi.integration.dsers;

import java.util.List;

/** Internal contract. It deliberately does not model an unverified DSers HTTP payload. */
public record DsersOrderSubmission(
        String externalReference,
        Recipient recipient,
        List<Item> items
) {
    public record Recipient(String name, String zipCode, String street, String number,
                            String complement, String neighborhood, String city, String state,
                            String countryCode) { }
    public record Item(String supplierProductId, String supplierVariantId, String supplierSku,
                       int quantity, String localItemReference) { }
}

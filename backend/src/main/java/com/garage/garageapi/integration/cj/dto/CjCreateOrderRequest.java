package com.garage.garageapi.integration.cj.dto;

import java.util.List;

public record CjCreateOrderRequest(
        String orderNumber,
        String shippingZip,
        String shippingCountry,
        String shippingCountryCode,
        String shippingProvince,
        String shippingCity,
        String shippingCustomerName,
        String shippingAddress,
        String shippingAddress2,
        String houseNumber,
        int payType,
        String logisticName,
        String fromCountryCode,
        int orderFlow,
        List<Product> products
) {
    public record Product(String vid, int quantity, String storeLineItemId) { }
}

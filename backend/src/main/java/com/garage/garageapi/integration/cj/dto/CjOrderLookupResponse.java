package com.garage.garageapi.integration.cj.dto;

public record CjOrderLookupResponse(String orderId, String shipmentOrderId,
                                    String orderNumber, String orderStatus) { }

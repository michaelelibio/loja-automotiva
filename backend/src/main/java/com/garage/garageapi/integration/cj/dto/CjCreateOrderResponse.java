package com.garage.garageapi.integration.cj.dto;

public record CjCreateOrderResponse(String orderId, String shipmentOrderId,
                                    String orderNumber, String orderStatus) { }

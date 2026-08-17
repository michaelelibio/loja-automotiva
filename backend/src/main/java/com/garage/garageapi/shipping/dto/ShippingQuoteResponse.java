package com.garage.garageapi.shipping.dto;

import java.util.List;

public record ShippingQuoteResponse(List<ShippingOptionResponse> options) { }

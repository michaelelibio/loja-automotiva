package com.garage.garageapi.integration.aliexpress;

/** IDs remain strings end to end, including SKU IDs larger than JavaScript's safe integer. */
public record AliExpressFreightDiagnosticRequest(String productId, String skuId, Integer quantity) {}

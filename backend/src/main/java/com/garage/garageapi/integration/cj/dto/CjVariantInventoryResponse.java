package com.garage.garageapi.integration.cj.dto;

import java.util.List;

public record CjVariantInventoryResponse(String variantId, List<Warehouse> warehouses) {
    public record Warehouse(String warehouseId, String warehouseName, String countryCode,
                            int totalInventory) { }
}

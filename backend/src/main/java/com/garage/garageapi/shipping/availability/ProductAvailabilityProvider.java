package com.garage.garageapi.shipping.availability;

import java.util.List;

public interface ProductAvailabilityProvider {
    Availability check(String supplierVariantId, int quantity);

    record Availability(boolean available, List<Warehouse> warehouses) { }
    record Warehouse(String warehouseId, String countryCode, int availableQuantity) { }
}

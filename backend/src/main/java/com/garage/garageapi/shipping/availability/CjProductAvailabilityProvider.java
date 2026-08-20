package com.garage.garageapi.shipping.availability;

import com.garage.garageapi.integration.cj.service.CjCommerceService;
import org.springframework.stereotype.Component;

@Component
public class CjProductAvailabilityProvider implements ProductAvailabilityProvider {
    private final CjCommerceService commerceService;

    public CjProductAvailabilityProvider(CjCommerceService commerceService) {
        this.commerceService = commerceService;
    }

    @Override
    public Availability check(String supplierVariantId, int quantity) {
        var response = commerceService.inventory(supplierVariantId);
        var warehouses = response.warehouses().stream()
                .map(item -> new Warehouse(item.warehouseId(), item.countryCode(),
                        item.totalInventory())).toList();
        return new Availability(warehouses.stream()
                .anyMatch(warehouse -> warehouse.availableQuantity() >= quantity), warehouses);
    }
}

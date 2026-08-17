package com.garage.garageapi.admin.stock.dto;

import com.garage.garageapi.stock.entity.StockMovementType;

public enum AdminStockMovementType {
    PURCHASE_ENTRY,
    MANUAL_ADJUSTMENT_IN,
    MANUAL_ADJUSTMENT_OUT;

    public StockMovementType toEntityType() { return StockMovementType.valueOf(name()); }
}

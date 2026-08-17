package com.garage.garageapi.admin.stock.dto;

public record AdminStockSummaryResponse(long totalProducts, long totalUnits,
                                        long outOfStockProducts) { }

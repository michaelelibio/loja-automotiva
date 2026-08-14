package com.garage.garageapi.vehicle.dto;

import com.garage.garageapi.vehicle.entity.Vehicle;

import java.time.Instant;

public record VehicleResponse(Long id, String brand, String model, Integer year, String version,
                              String licensePlate, boolean isPrimary, String imageUrl, Instant createdAt,
                              Instant updatedAt) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
                vehicle.getYear(), vehicle.getVersion(), vehicle.getLicensePlate(),
                vehicle.isPrimary(), vehicle.getImageUrl(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}

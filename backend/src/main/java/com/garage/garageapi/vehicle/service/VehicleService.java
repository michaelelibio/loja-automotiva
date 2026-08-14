package com.garage.garageapi.vehicle.service;

import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import com.garage.garageapi.vehicle.dto.VehicleRequest;
import com.garage.garageapi.vehicle.dto.VehicleResponse;
import com.garage.garageapi.vehicle.entity.Vehicle;
import com.garage.garageapi.vehicle.repository.VehicleRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final UserService userService;

    public VehicleService(VehicleRepository vehicleRepository, UserService userService) {
        this.vehicleRepository = vehicleRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(Jwt jwt) {
        User user = userService.findCurrentUser(jwt);
        return vehicleRepository.findAllByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId()).stream()
                .map(VehicleResponse::from).toList();
    }

    @Transactional
    public VehicleResponse create(Jwt jwt, VehicleRequest request) {
        User user = userService.findCurrentUser(jwt);
        boolean primary = vehicleRepository.countByUserId(user.getId()) == 0
                || Boolean.TRUE.equals(request.isPrimary());
        if (primary) clearPrimary(user.getId());
        Vehicle vehicle = new Vehicle(user, normalizeRequired(request.brand()),
                normalizeRequired(request.model()), request.year(), normalizeOptional(request.version()),
                normalizePlate(request.licensePlate()), primary, normalizeOptional(request.imageUrl()));
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleResponse update(Jwt jwt, Long id, VehicleRequest request) {
        User user = userService.findCurrentUser(jwt);
        Vehicle vehicle = ownedVehicle(id, user.getId());
        boolean primary = Boolean.TRUE.equals(request.isPrimary());
        if (primary) clearPrimary(user.getId());
        vehicle.update(normalizeRequired(request.brand()), normalizeRequired(request.model()),
                request.year(), normalizeOptional(request.version()),
                normalizePlate(request.licensePlate()), primary, normalizeOptional(request.imageUrl()));
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public void delete(Jwt jwt, Long id) {
        User user = userService.findCurrentUser(jwt);
        Vehicle vehicle = ownedVehicle(id, user.getId());
        boolean wasPrimary = vehicle.isPrimary();
        vehicleRepository.delete(vehicle);
        vehicleRepository.flush();
        if (wasPrimary) {
            vehicleRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                    .ifPresent(replacement -> replacement.setPrimary(true));
        }
    }

    @Transactional
    public VehicleResponse setPrimary(Jwt jwt, Long id) {
        User user = userService.findCurrentUser(jwt);
        Vehicle vehicle = ownedVehicle(id, user.getId());
        clearPrimary(user.getId());
        vehicle.setPrimary(true);
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    private Vehicle ownedVehicle(Long id, Long userId) {
        return vehicleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado: " + id));
    }

    private void clearPrimary(Long userId) {
        vehicleRepository.findAllByUserIdAndPrimaryTrue(userId)
                .forEach(vehicle -> vehicle.setPrimary(false));
    }

    private String normalizeRequired(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return normalizeRequired(value);
    }

    private String normalizePlate(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}

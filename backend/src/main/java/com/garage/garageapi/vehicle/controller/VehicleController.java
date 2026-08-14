package com.garage.garageapi.vehicle.controller;

import com.garage.garageapi.vehicle.dto.VehicleRequest;
import com.garage.garageapi.vehicle.dto.VehicleResponse;
import com.garage.garageapi.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return vehicleService.list(jwt);
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(jwt, request));
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                  @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(jwt, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        vehicleService.delete(jwt, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/primary")
    public VehicleResponse setPrimary(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vehicleService.setPrimary(jwt, id);
    }
}

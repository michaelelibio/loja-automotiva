package com.garage.garageapi.address.controller;

import com.garage.garageapi.address.dto.AddressRequest;
import com.garage.garageapi.address.dto.AddressResponse;
import com.garage.garageapi.address.service.AddressService;
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
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) { this.addressService = addressService; }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return addressService.list(jwt);
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(jwt, request));
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(jwt, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        addressService.delete(jwt, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/primary")
    public AddressResponse setPrimary(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return addressService.setPrimary(jwt, id);
    }
}

package com.garage.garageapi.address.service;

import com.garage.garageapi.address.dto.AddressRequest;
import com.garage.garageapi.address.dto.AddressResponse;
import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final UserService userService;

    public AddressService(AddressRepository addressRepository, UserService userService) {
        this.addressRepository = addressRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Jwt jwt) {
        User user = userService.findCurrentUser(jwt);
        return addressRepository.findAllByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId()).stream()
                .map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse create(Jwt jwt, AddressRequest request) {
        User user = userService.findCurrentUser(jwt);
        boolean primary = addressRepository.countByUserId(user.getId()) == 0
                || Boolean.TRUE.equals(request.isPrimary());
        if (primary) clearPrimary(user.getId());
        Address address = new Address(user, normalizeOptional(request.label()),
                normalizeRequired(request.recipientName()), normalizeZipCode(request.zipCode()),
                normalizeRequired(request.street()), request.number().trim(),
                normalizeOptional(request.complement()), normalizeRequired(request.neighborhood()),
                normalizeRequired(request.city()), normalizeState(request.state()), primary);
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse update(Jwt jwt, Long id, AddressRequest request) {
        User user = userService.findCurrentUser(jwt);
        Address address = ownedAddress(id, user.getId());
        if (Boolean.TRUE.equals(request.isPrimary())) {
            clearPrimary(user.getId());
            address.setPrimary(true);
        }
        address.update(normalizeOptional(request.label()), normalizeRequired(request.recipientName()),
                normalizeZipCode(request.zipCode()), normalizeRequired(request.street()),
                request.number().trim(), normalizeOptional(request.complement()),
                normalizeRequired(request.neighborhood()), normalizeRequired(request.city()),
                normalizeState(request.state()));
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Transactional
    public void delete(Jwt jwt, Long id) {
        User user = userService.findCurrentUser(jwt);
        Address address = ownedAddress(id, user.getId());
        boolean wasPrimary = address.isPrimary();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasPrimary) {
            addressRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                    .ifPresent(replacement -> replacement.setPrimary(true));
        }
    }

    @Transactional
    public AddressResponse setPrimary(Jwt jwt, Long id) {
        User user = userService.findCurrentUser(jwt);
        Address address = ownedAddress(id, user.getId());
        clearPrimary(user.getId());
        address.setPrimary(true);
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    private Address ownedAddress(Long id, Long userId) {
        return addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado: " + id));
    }

    private void clearPrimary(Long userId) {
        addressRepository.findAllByUserIdAndPrimaryTrue(userId)
                .forEach(address -> address.setPrimary(false));
    }

    private String normalizeRequired(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return normalizeRequired(value);
    }

    private String normalizeZipCode(String zipCode) { return zipCode.replaceAll("\\D", ""); }

    private String normalizeState(String state) { return state.trim().toUpperCase(Locale.ROOT); }
}

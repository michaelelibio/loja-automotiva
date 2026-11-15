package com.garage.garageapi.shipping.eligibility;

import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.user.service.UserService;
import com.garage.garageapi.shipping.service.ShippingService;
import com.garage.garageapi.shipping.dto.*;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/orders/delivery-eligibility")
public class DeliveryEligibilityController {
    private final AddressRepository addresses;
    private final UserService users;
    private final ShippingService products;
    private final CatalogFreeDelivery delivery;
    public DeliveryEligibilityController(AddressRepository addresses, UserService users,
            ShippingService products, CatalogFreeDelivery delivery) {
        this.addresses = addresses; this.users = users; this.products = products; this.delivery = delivery;
    }
    public record Request(@NotNull Long addressId, @Valid @NotNull ShippingQuoteRequest cart) { }
    @PostMapping
    @Transactional(readOnly = true)
    public ShippingQuoteResponse check(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
        var user = users.findCurrentUser(jwt);
        var address = addresses.findByIdAndUserId(request.addressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado."));
        var option = delivery.select(products.resolveItems(request.cart()));
        return new ShippingQuoteResponse(List.of(ShippingOptionResponse.from(option)));
    }
}

package com.garage.garageapi.shipping.controller;

import com.garage.garageapi.shipping.dto.ShippingQuoteRequest;
import com.garage.garageapi.shipping.dto.ShippingQuoteResponse;
import com.garage.garageapi.shipping.service.ShippingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {
    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping("/quote")
    public ShippingQuoteResponse quote(@Valid @RequestBody ShippingQuoteRequest request) {
        return shippingService.quote(request);
    }
}

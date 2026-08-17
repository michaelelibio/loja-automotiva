package com.garage.garageapi.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ShippingQuoteRequest(
        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido") String zipCode,
        @NotEmpty(message = "cotação deve possuir ao menos um item")
        List<@Valid ShippingQuoteItemRequest> items
) { }

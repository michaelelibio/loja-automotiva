package com.garage.garageapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "credencial Google é obrigatória") String credential
) {
}

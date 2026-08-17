package com.garage.garageapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank(message = "token é obrigatório") String token) { }

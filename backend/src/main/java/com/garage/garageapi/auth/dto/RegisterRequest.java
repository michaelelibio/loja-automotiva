package com.garage.garageapi.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "nome é obrigatório") @Size(max = 150) String name,
        @NotBlank(message = "e-mail é obrigatório") @Email(message = "e-mail inválido") @Size(max = 320) String email,
        @NotBlank(message = "senha é obrigatória") @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres") String password
) {
}

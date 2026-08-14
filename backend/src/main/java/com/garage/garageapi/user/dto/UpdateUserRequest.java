package com.garage.garageapi.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(min = 2, max = 150, message = "nome deve ter entre 2 e 150 caracteres")
        @Pattern(regexp = "^(?=(?:.*\\S){2}).*$", message = "nome deve ter ao menos 2 caracteres")
        String name
) {
}

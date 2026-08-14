package com.garage.garageapi.vehicle.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Year;

public record VehicleRequest(
        @NotBlank(message = "marca é obrigatória")
        @Size(max = 80, message = "marca deve ter no máximo 80 caracteres")
        String brand,
        @NotBlank(message = "modelo é obrigatório")
        @Size(max = 120, message = "modelo deve ter no máximo 120 caracteres")
        String model,
        @NotNull(message = "ano é obrigatório")
        Integer year,
        @Size(max = 150, message = "versão deve ter no máximo 150 caracteres")
        String version,
        @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z]{3}[0-9][A-Za-z0-9][0-9]{2}\\s*$",
                message = "placa deve estar no formato AAA1234 ou AAA1A23")
        String licensePlate,
        Boolean isPrimary,
        @Size(max = 1000, message = "URL da imagem deve ter no máximo 1000 caracteres")
        String imageUrl
) {
    public static final int MIN_YEAR = 1886;

    @AssertTrue(message = "ano deve estar entre 1886 e o próximo ano")
    public boolean isYearValid() {
        return year == null || year >= MIN_YEAR && year <= Year.now().getValue() + 1;
    }
}

package com.garage.garageapi.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 50, message = "identificação deve ter no máximo 50 caracteres")
        String label,
        @NotBlank(message = "nome do destinatário é obrigatório")
        @Size(max = 150, message = "nome do destinatário deve ter no máximo 150 caracteres")
        String recipientName,
        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "^\\s*\\d{5}-?\\d{3}\\s*$", message = "CEP deve possuir 8 dígitos")
        String zipCode,
        @NotBlank(message = "rua é obrigatória")
        @Size(max = 200, message = "rua deve ter no máximo 200 caracteres")
        String street,
        @NotBlank(message = "número é obrigatório")
        @Size(max = 30, message = "número deve ter no máximo 30 caracteres")
        String number,
        @Size(max = 150, message = "complemento deve ter no máximo 150 caracteres")
        String complement,
        @NotBlank(message = "bairro é obrigatório")
        @Size(max = 120, message = "bairro deve ter no máximo 120 caracteres")
        String neighborhood,
        @NotBlank(message = "cidade é obrigatória")
        @Size(max = 120, message = "cidade deve ter no máximo 120 caracteres")
        String city,
        @NotBlank(message = "UF é obrigatória")
        @Pattern(regexp = "^\\s*[A-Za-z]{2}\\s*$", message = "UF deve possuir exatamente 2 letras")
        String state,
        Boolean isPrimary
) { }

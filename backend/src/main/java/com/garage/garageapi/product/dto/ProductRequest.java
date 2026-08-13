package com.garage.garageapi.product.dto;

import com.garage.garageapi.product.entity.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank
        @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "deve conter apenas letras minúsculas, números e hífens")
        String slug,
        @Size(max = 1000) String description,
        @Size(max = 5000) String longDescription,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @DecimalMin(value = "0.01") BigDecimal oldPrice,
        @NotBlank @Size(max = 100) String category,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @Size(max = 500) String imageUrl,
        Boolean active,
        ProductType productType
) {
}

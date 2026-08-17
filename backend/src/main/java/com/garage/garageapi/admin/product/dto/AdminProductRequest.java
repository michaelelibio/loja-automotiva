package com.garage.garageapi.admin.product.dto;

import com.garage.garageapi.product.entity.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminProductRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @Size(max = 1000) String description,
        @Size(max = 5000) String longDescription,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @DecimalMin("0.01") BigDecimal oldPrice,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal costPrice,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull Boolean active,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String sku,
        @Size(max = 500) String imageUrl,
        ProductType productType
) { }

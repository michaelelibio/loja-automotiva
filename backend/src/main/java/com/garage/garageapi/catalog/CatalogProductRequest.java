package com.garage.garageapi.catalog;
import jakarta.validation.constraints.*;
import java.util.List;
public record CatalogProductRequest(
        @Positive Long categoryId,@Positive Long subcategoryId,@Size(max=100) String brand,
        @Size(max=30) List<@NotBlank @Size(max=100) String> surfaces,
        @Size(max=30) List<@NotBlank @Size(max=100) String> purposes,
        @Size(max=40) List<@NotBlank @Size(max=100) String> searchTerms,
        @Pattern(regexp="DOMESTIC|PROFESSIONAL|BOTH") String usage,
        Boolean concentrated,Boolean refill,Boolean featured,Boolean beginner,
        @Pattern(regexp="NATIONAL|INTERNATIONAL") String commercialOrigin) { }

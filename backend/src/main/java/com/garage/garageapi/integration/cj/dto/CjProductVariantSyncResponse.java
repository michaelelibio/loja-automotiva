package com.garage.garageapi.integration.cj.dto;

public record CjProductVariantSyncResponse(
        int created,
        int updated,
        int unchanged
) {}
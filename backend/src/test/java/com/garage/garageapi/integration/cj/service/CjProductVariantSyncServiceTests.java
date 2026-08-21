package com.garage.garageapi.integration.cj.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CjProductVariantSyncServiceTests {
    @Test
    void mapsConsistentProductKeysAndVariantValuesSemantically() {
        assertThat(CjProductVariantSyncService.semanticAttributes(
                "Color-Size", "Black-XXL"))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "Color", "Black", "Size", "XXL"));
    }

    @Test
    void fallsBackWithoutProductKeysOrWithDifferentCardinality() {
        assertThat(CjProductVariantSyncService.semanticAttributes(null, "Black-XXL"))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "option1", "Black", "option2", "XXL"));
        assertThat(CjProductVariantSyncService.semanticAttributes("Color", "Black-XXL"))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "option1", "Black", "option2", "XXL"));
    }

    @Test
    void doesNotGuessAmbiguousHyphenatedValues() {
        assertThat(CjProductVariantSyncService.semanticAttributes(
                "Model-Color", "Model-With-Hyphen-Black"))
                .containsEntry("option1", "Model")
                .containsEntry("option4", "Black")
                .doesNotContainKeys("Model", "Color");
    }
}

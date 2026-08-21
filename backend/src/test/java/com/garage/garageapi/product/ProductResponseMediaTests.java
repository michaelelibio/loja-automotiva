package com.garage.garageapi.product;

import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductMedia;
import com.garage.garageapi.product.entity.ProductMediaSource;
import com.garage.garageapi.product.entity.ProductMediaType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseMediaTests {
    @Test
    void legacyProductKeepsImageUrlAndReturnsEmptyMedia() {
        Product product = product();
        ProductResponse response = ProductResponse.from(product);

        assertThat(response.imageUrl()).isEqualTo("https://garage.test/legacy.jpg");
        assertThat(response.media()).isEmpty();
        assertThat(response.variants()).isEmpty();
    }

    @Test
    void exposesOnlyActiveMediaOrderedByPosition() {
        Product product = product();
        product.getMedia().add(new ProductMedia(product, ProductMediaType.IMAGE,
                "https://garage.test/two.jpg", null, 2, "Two", ProductMediaSource.MANUAL));
        product.getMedia().add(new ProductMedia(product, ProductMediaType.IMAGE,
                "https://garage.test/one.jpg", null, 1, "One", ProductMediaSource.MANUAL));
        ProductMedia inactive = new ProductMedia(product, ProductMediaType.IMAGE,
                "https://garage.test/off.jpg", null, 0, "Off", ProductMediaSource.MANUAL);
        inactive.setActive(false);
        product.getMedia().add(inactive);

        assertThat(ProductResponse.from(product).media()).extracting(media -> media.url())
                .containsExactly("https://garage.test/one.jpg", "https://garage.test/two.jpg");
    }

    private Product product() {
        return new Product("Legacy", "legacy", null, null, BigDecimal.TEN, null,
                "Category", 1, "https://garage.test/legacy.jpg", true);
    }
}

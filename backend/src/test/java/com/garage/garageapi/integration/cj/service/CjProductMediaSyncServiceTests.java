package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductMedia;
import com.garage.garageapi.product.entity.ProductMediaSource;
import com.garage.garageapi.product.entity.ProductMediaType;
import com.garage.garageapi.product.repository.ProductMediaRepository;
import com.garage.garageapi.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CjProductMediaSyncServiceTests {
    @Autowired CjProductMediaSyncService syncService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductMediaRepository mediaRepository;

    @Test
    void importsGalleryInOrderAndDeduplicatesMainAndInvalidUrls() {
        Product product = product("gallery");

        syncService.syncImages(product, "https://cdn.test/main.jpg", List.of(
                "https://cdn.test/one.jpg", "https://cdn.test/main.jpg",
                " ", "not-a-url", "https://cdn.test/one.jpg"));

        assertThat(activeCj(product)).extracting(ProductMedia::getUrl)
                .containsExactly("https://cdn.test/one.jpg", "https://cdn.test/main.jpg");
    }

    @Test
    void insertsMainFirstWhenMissingFromGalleryAndSupportsNullOrEmptyGallery() {
        Product product = product("missing-main");
        syncService.syncImages(product, "https://cdn.test/main.jpg",
                List.of("https://cdn.test/other.jpg"));
        assertThat(activeCj(product)).extracting(ProductMedia::getUrl)
                .containsExactly("https://cdn.test/main.jpg", "https://cdn.test/other.jpg");

        Product nullGallery = product("null-gallery");
        syncService.syncImages(nullGallery, "https://cdn.test/null.jpg", null);
        assertThat(activeCj(nullGallery)).extracting(ProductMedia::getUrl)
                .containsExactly("https://cdn.test/null.jpg");

        Product emptyGallery = product("empty-gallery");
        syncService.syncImages(emptyGallery, "https://cdn.test/empty.jpg", List.of());
        assertThat(activeCj(emptyGallery)).extracting(ProductMedia::getUrl)
                .containsExactly("https://cdn.test/empty.jpg");
    }

    @Test
    void repeatedSyncDoesNotDuplicateAndNeverChangesManualMedia() {
        Product product = product("repeat");
        ProductMedia manual = mediaRepository.save(new ProductMedia(product,
                ProductMediaType.IMAGE, "https://garage.test/manual.jpg", null, 99,
                "Manual", ProductMediaSource.MANUAL));

        syncService.syncImages(product, "https://cdn.test/main.jpg",
                List.of("https://cdn.test/main.jpg", "https://cdn.test/old.jpg"));
        syncService.syncImages(product, "https://cdn.test/main.jpg",
                List.of("https://cdn.test/main.jpg", "https://cdn.test/new.jpg"));

        assertThat(activeCj(product)).extracting(ProductMedia::getUrl)
                .containsExactly("https://cdn.test/main.jpg", "https://cdn.test/new.jpg");
        assertThat(mediaRepository.findById(manual.getId()).orElseThrow()).satisfies(current -> {
            assertThat(current.getUrl()).isEqualTo("https://garage.test/manual.jpg");
            assertThat(current.getPosition()).isEqualTo(99);
            assertThat(current.getActive()).isTrue();
        });
        assertThat(mediaRepository.findAll()).hasSize(4);
    }

    private List<ProductMedia> activeCj(Product product) {
        return mediaRepository.findAllByProductIdAndSourceOrderByPositionAscIdAsc(
                        product.getId(), ProductMediaSource.CJ).stream()
                .filter(media -> Boolean.TRUE.equals(media.getActive())).toList();
    }

    private Product product(String slug) {
        return productRepository.saveAndFlush(new Product("Product " + slug, slug, null, null,
                BigDecimal.TEN, null, "Category", 1, null, true));
    }
}

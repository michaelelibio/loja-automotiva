package com.garage.garageapi.product;

import com.garage.garageapi.product.config.ProductCatalogSeeder;
import com.garage.garageapi.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed.products.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:catalog-seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class ProductCatalogSeederTests {
    @Autowired ProductCatalogSeeder seeder;
    @Autowired ProductRepository productRepository;

    @Test
    void seedsCompleteCatalogAndIsIdempotent() throws Exception {
        assertThat(productRepository.count()).isEqualTo(10);
        assertThat(productRepository.findBySlug("cera-automotiva-premium")).isPresent();
        assertThat(productRepository.findBySlug("kit-de-limpeza-automotiva")).isPresent();
        assertThat(productRepository.findAll())
                .allSatisfy(product -> {
                    assertThat(product.getImageUrl()).isNull();
                    assertThat(product.getActive()).isTrue();
                    assertThat(product.getProductType()).isEqualTo(com.garage.garageapi.product.entity.ProductType.SINGLE);
                });

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(productRepository.count()).isEqualTo(10);
    }
}

package com.garage.garageapi.product;

import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTypeTests {

    @Test
    void productDefaultsToSingle() {
        Product product = new Product("Produto", "produto", null, null,
                new BigDecimal("10.00"), null, "Categoria", 1, null, true);

        assertThat(product.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(ProductResponse.from(product).productType()).isEqualTo(ProductType.SINGLE);
    }

    @Test
    void productCanBeClassifiedAsKit() {
        Product product = new Product("Kit", "kit", null, null,
                new BigDecimal("20.00"), null, "Categoria", 1, null, true, ProductType.KIT);

        assertThat(ProductResponse.from(product).productType()).isEqualTo(ProductType.KIT);
    }
}

package com.garage.garageapi.product;

import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductFulfillmentTests {
    @Test
    void localStockAvailabilityDependsOnActiveStatusAndBalance() {
        Product available = product(true, 1);
        Product empty = product(true, 0);

        assertThat(available.getFulfillmentType()).isEqualTo(FulfillmentType.LOCAL_STOCK);
        assertThat(available.isAvailableForSale()).isTrue();
        assertThat(empty.isAvailableForSale()).isFalse();
    }

    @Test
    void dropshippingAvailabilityDependsOnActiveStatusButNotLocalBalance() {
        Product active = product(true, 0);
        active.configureFulfillment(FulfillmentType.DROPSHIPPING);
        Product inactive = product(false, 0);
        inactive.configureFulfillment(FulfillmentType.DROPSHIPPING);

        assertThat(active.isAvailableForSale()).isTrue();
        assertThat(active.canFulfill(5)).isTrue();
        assertThat(inactive.isAvailableForSale()).isFalse();
    }

    @Test
    void dropshippingRejectsLocalStockMutation() {
        Product product = product(true, 0);
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);

        assertThatThrownBy(() -> product.increaseStock(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.decreaseStock(1)).isInstanceOf(IllegalArgumentException.class);
    }

    private Product product(boolean active, int stock) {
        return new Product("Produto", "produto", null, null, BigDecimal.TEN, null,
                "Categoria", stock, null, active);
    }
}

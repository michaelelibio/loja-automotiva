package com.garage.garageapi.integration.cj.currency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredExchangeRateServiceTests {
    @Test
    void returnsConfiguredBigDecimalRate() {
        var service = new ConfiguredExchangeRateService(new BigDecimal("5.50"));
        assertThat(service.usdToBrl()).isEqualByComparingTo("5.50");
    }

    @Test
    void rejectsZeroAndNegativeRates() {
        assertThatThrownBy(() -> new ConfiguredExchangeRateService(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredExchangeRateService(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
